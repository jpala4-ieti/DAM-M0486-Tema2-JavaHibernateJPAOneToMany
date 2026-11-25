package com.project;

import jakarta.persistence.*;
import java.io.Serializable;

// @Entity: Marca aquesta classe com una entitat JPA mapejada a la taula "Item".
@Entity
@Table(name = "Item")
public class Item implements Serializable {

    // CLAU PRIMÀRIA amb auto-increment gestionat per la BBDD.
    // Long (i no long): mentre l'entitat no s'ha desat, la clau és null.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "itemId", unique = true, nullable = false)
    private Long itemId;

    @Column(name = "name")
    private String name;

    // RELACIÓ MANY-TO-ONE (Molts Items pertanyen a un Cart):
    // - Aquest és el costat PROPIETARI de la relació: la taula Item té la clau forana.
    // - @JoinColumn: Defineix la columna FK "cartId" a la taula Item.
    // - FetchType.LAZY: No carrega el Cart fins que s'hi accedeix (per defecte seria EAGER).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartId")
    private Cart cart;

    // Constructor sense arguments: obligatori, Hibernate crea els objectes amb reflexió.
    public Item() {}

    public Item(String name) {
        this.name = name;
    }

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Cart getCart() { return cart; }
    public void setCart(Cart cart) { this.cart = cart; }

    // toString() sense el Cart: en una relació bidireccional provocaria
    // consultes inesperades (LAZY) o recursió infinita.
    @Override
    public String toString() {
        return itemId + ": " + name;
    }

    // EQUALS i HASHCODE estables, basats en l'id:
    // - Dues entitats són iguals si tenen el mateix id (una entitat sense id només és igual a ella mateixa).
    // - hashCode constant: no canvia quan la BBDD assigna l'id, de manera que l'entitat
    //   es pot desar en un Set abans i després de persistir-la.
    // - instanceof (i no getClass()): Hibernate crea PROXIES (subclasses) per al lazy loading.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Item other)) return false;
        return itemId != null && itemId.equals(other.getItemId());
    }

    @Override
    public int hashCode() {
        return Item.class.hashCode();
    }
}
