package com.project;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;

/**
 * Classe MANAGER: Patró DAO (Data Access Object)
 * Centralitza totes les operacions amb la base de dades.
 * Gestiona el cicle de vida de les sessions Hibernate.
 *
 * El mapeig es fa amb anotacions de JPA (jakarta.persistence) i es treballa
 * amb l'API nativa de Hibernate: SessionFactory i Session, que a JPA
 * s'anomenen EntityManagerFactory i EntityManager.
 */
public class Manager {

    // SESSIONFACTORY: Objecte pesat que es crea UNA SOLA VEGADA.
    // Guarda el mapa classe <-> taula i la connexió, i fabrica les sessions.
    private static SessionFactory factory;

    // ═══════════════════════════════════════════════════════════════════
    // INICIALITZACIÓ DE HIBERNATE
    // ═══════════════════════════════════════════════════════════════════

    // Configuration llegeix automàticament el fitxer hibernate.properties del classpath.
    public static void createSessionFactory() {
        factory = newConfiguration().buildSessionFactory();
    }

    // Variant per fer servir un altre fitxer de propietats (per exemple hibernate-mysql.properties).
    // Les seves propietats s'afegeixen a les de hibernate.properties i les sobreescriuen.
    public static void createSessionFactory(String propertiesFileName) {
        Properties properties = new Properties();
        try (InputStream input = Manager.class.getClassLoader().getResourceAsStream(propertiesFileName)) {
            if (input == null) {
                throw new IllegalArgumentException("No s'ha pogut trobar " + propertiesFileName);
            }
            properties.load(input);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        factory = newConfiguration().addProperties(properties).buildSessionFactory();
    }

    // Amb anotacions JPA cal indicar a Hibernate quines classes són entitats.
    private static Configuration newConfiguration() {
        return new Configuration()
                .addAnnotatedClass(Cart.class)
                .addAnnotatedClass(Item.class);
    }

    public static void close() {
        if (factory != null) factory.close();
    }

    // ═══════════════════════════════════════════════════════════════════
    // CRUD - CREATE (Creació d'entitats)
    // ═══════════════════════════════════════════════════════════════════

    // Versió EXPLÍCITA, per veure totes les peces d'una transacció.
    public static Cart addCart(String type) {
        // TRY-WITH-RESOURCES: la Session es tanca sola en acabar el bloc.
        try (Session session = factory.openSession()) {
            // TRANSACTION: agrupa les operacions. O es fan totes o no se'n fa cap.
            Transaction tx = session.beginTransaction();
            try {
                Cart cart = new Cart(type);
                // PERSIST: Hibernate passa a gestionar l'objecte i li assigna un id.
                session.persist(cart);
                // COMMIT: aquí es confirmen els canvis a la BBDD.
                tx.commit();
                return cart;
            } catch (Exception e) {
                // ROLLBACK: davant de qualsevol excepció es desfan els canvis.
                tx.rollback();
                throw e;
            }
        }
    }

    // Versió CURTA i equivalent: fromTransaction obre la sessió, fa commit
    // (o rollback si hi ha una excepció), la tanca i retorna el resultat.
    // La resta de mètodes fan servir aquesta forma (inTransaction si no retornen res).
    public static Item addItem(String name) {
        return factory.fromTransaction(session -> {
            Item item = new Item(name);
            session.persist(item);
            return item;
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // CRUD - UPDATE (Actualització d'entitats)
    // ═══════════════════════════════════════════════════════════════════

    public static void updateItem(Long itemId, String name) {
        factory.inTransaction(session -> {
            // FIND: Recupera l'entitat per ID. Retorna null si no existeix.
            Item item = session.find(Item.class, itemId);
            if (item != null) {
                // DIRTY CHECKING: n'hi ha prou de modificar l'objecte. En fer commit,
                // Hibernate el compara amb com era quan el va carregar i genera l'UPDATE.
                // No cal cridar cap update() ni merge().
                item.setName(name);
                System.out.println("Item " + itemId + " actualitzat.");
            }
        });
    }

    // Deixa el Cart amb el tipus indicat i exactament amb els items de la llista.
    // Si items és null o buit, el Cart queda sense items.
    public static void updateCart(Long cartId, String type, Set<Item> items) {
        factory.inTransaction(session -> {
            Cart cart = session.find(Cart.class, cartId);

            if (cart == null) {
                System.err.println("Cart no trobat amb id: " + cartId);
                return;
            }

            cart.setType(type);

            // Còpia de la llista rebuda: pot ser la col·lecció d'un Cart carregat en una altra sessió.
            Set<Item> desired = (items == null) ? new HashSet<>() : new HashSet<>(items);

            // PAS 1: Treure els items que ja no són a la nova llista.
            // Es recorre una còpia per evitar ConcurrentModificationException.
            // ATENCIÓ: amb orphanRemoval = true, l'item tret s'esborra de la BBDD.
            for (Item current : new HashSet<>(cart.getItems())) {
                if (!desired.contains(current)) {
                    cart.removeItem(current);
                }
            }

            // PAS 2: Afegir els items que falten.
            for (Item itemInput : desired) {
                if (itemInput.getItemId() == null) {
                    // Item nou, sense id: es desarà sol gràcies al CASCADE.
                    cart.addItem(itemInput);
                } else {
                    // Item que ja existeix: es recupera l'entitat gestionada per AQUESTA sessió.
                    Item managedItem = session.find(Item.class, itemInput.getItemId());
                    if (managedItem != null && !cart.getItems().contains(managedItem)) {
                        cart.addItem(managedItem);
                    }
                }
            }

            // No cal merge(): el Cart l'ha carregat aquesta sessió i el dirty checking
            // detecta tots els canvis en fer commit.
            System.out.println("Cart " + cartId + " actualitzat.");
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // CRUD - READ (Lectura d'entitats)
    // ═══════════════════════════════════════════════════════════════════

    public static Cart getCartWithItems(Long cartId) {
        // JOIN FETCH: Carrega Cart + Items en UNA SOLA consulta SQL.
        // Com que items és LAZY, sense això accedir a getItems() fora de la sessió
        // llançaria LazyInitializationException.
        // Paràmetre amb nom (:id): mai es concatenen valors dins d'una consulta.
        return factory.fromSession(session ->
            session.createQuery("SELECT c FROM Cart c LEFT JOIN FETCH c.items WHERE c.cartId = :id", Cart.class)
                   .setParameter("id", cartId)
                   .uniqueResult());
    }

    // MÈTODE GENÈRIC: Funciona amb qualsevol classe Entity gràcies a <T>
    public static <T> List<T> findAll(Class<T> clazz) {
        // HQL (Hibernate Query Language): Similar a SQL però usa noms de classes Java.
        // Aquí es concatena el nom de la classe (no una dada de l'usuari), i la consulta és tipada.
        return factory.fromSession(session ->
            session.createQuery("FROM " + clazz.getName(), clazz).getResultList());
    }

    public static List<Cart> findAllCartsWithItems() {
        // Una sola consulta que ja porta els items de cada Cart (evita el problema N+1).
        return factory.fromSession(session ->
            session.createQuery("SELECT c FROM Cart c LEFT JOIN FETCH c.items", Cart.class).getResultList());
    }

    // ═══════════════════════════════════════════════════════════════════
    // CRUD - DELETE (Eliminació d'entitats)
    // ═══════════════════════════════════════════════════════════════════

    // MÈTODE GENÈRIC amb Serializable: Funciona amb Long, Integer, String com a ID
    public static <T> void delete(Class<T> clazz, Serializable id) {
        factory.inTransaction(session -> {
            T obj = session.find(clazz, id);
            if (obj != null) {
                // REMOVE: Elimina l'entitat de la BBDD.
                // En un Cart, el cascade elimina també els seus items.
                session.remove(obj);
                System.out.println("Eliminat objecte " + clazz.getSimpleName() + " amb id " + id);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITATS
    // ═══════════════════════════════════════════════════════════════════

    public static <T> String collectionToString(Collection<T> collection) {
        StringBuilder sb = new StringBuilder();
        for (T obj : collection) {
            sb.append(obj.toString()).append("\n");
        }
        return sb.toString();
    }
}
