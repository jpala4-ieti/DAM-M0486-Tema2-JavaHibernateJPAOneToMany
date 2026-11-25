package com.project;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

// @Entity: Marca aquesta classe com una entitat JPA que es mapeja a una taula de la base de dades.
// Les anotacions són de JPA (jakarta.persistence); qui les llegeix i genera el SQL és Hibernate.
@Entity
@Table(name = "Cart")
public class Cart implements Serializable {

    // @Id: Defineix la clau primària de l'entitat.
    // @GeneratedValue(IDENTITY): La BBDD genera automàticament el valor (auto-increment).
    // Long (i no long): mentre l'entitat no s'ha desat, la clau és null.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cartId", unique = true, nullable = false)
    private Long cartId;

    @Column(name = "type")
    private String type;

    // RELACIÓ ONE-TO-MANY (Un Cart té molts Items). És el costat INVERS de la relació:
    // - mappedBy = "cart": la relació ja la mapeja el camp "cart" d'Item, que és el costat propietari.
    // - FetchType.LAZY: no carrega els items fins que s'hi accedeix.
    // - CascadeType.ALL: el que es fa al Cart (persist, remove...) es propaga als seus items.
    // - orphanRemoval = true: un item que es treu de la col·lecció s'esborra de la BBDD.
    // La col·lecció s'inicialitza per no trobar mai un null.
    @OneToMany(mappedBy = "cart", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Item> items = new HashSet<>();

    // Constructor sense arguments: obligatori, Hibernate crea els objectes amb reflexió.
    public Cart() {}

    public Cart(String type) {
        this.type = type;
    }

    public Long getCartId() { return cartId; }
    public void setCartId(Long cartId) { this.cartId = cartId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Set<Item> getItems() { return items; }

    // No se substitueix mai la col·lecció que gestiona Hibernate (this.items = ...):
    // es modifica amb els mètodes d'ajuda, que mantenen els dos costats sincronitzats.
    public void setItems(Set<Item> newItems) {
        Set<Item> desired = (newItems == null) ? new HashSet<>() : new HashSet<>(newItems);
        for (Item current : new HashSet<>(this.items)) {
            if (!desired.contains(current)) {
                removeItem(current);
            }
        }
        for (Item item : desired) {
            if (!this.items.contains(item)) {
                addItem(item);
            }
        }
    }

    // MÈTODES D'AJUDA: actualitzen ELS DOS costats de la relació.
    // - items.add(item): costat invers. L'item és a la col·lecció i el cascade el troba.
    // - item.setCart(this): costat propietari. És el que fa que Hibernate escrigui la clau forana.
    public void addItem(Item item) {
        items.add(item);
        item.setCart(this);
    }

    public void removeItem(Item item) {
        items.remove(item);
        item.setCart(null);
    }

    // toString() sense la col·lecció d'items: en una relació bidireccional provocaria
    // consultes inesperades (LAZY) o recursió infinita.
    @Override
    public String toString() {
        return String.format("Cart [ID=%d, Type=%s]", cartId, type);
    }

    // EQUALS i HASHCODE estables, basats en l'id:
    // - Dues entitats són iguals si tenen el mateix id (una entitat sense id només és igual a ella mateixa).
    // - hashCode constant: no canvia quan la BBDD assigna l'id, de manera que l'entitat
    //   es pot desar en un Set abans i després de persistir-la.
    // - instanceof (i no getClass()): Hibernate pot crear proxies, que són subclasses.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Cart other)) return false;
        return cartId != null && cartId.equals(other.getCartId());
    }

    @Override
    public int hashCode() {
        return Cart.class.hashCode();
    }
}
