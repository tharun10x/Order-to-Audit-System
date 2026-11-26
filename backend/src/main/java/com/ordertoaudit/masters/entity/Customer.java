package com.ordertoaudit.masters.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A retailer or distributor (R5.3). stateCode decides the place of supply. */
@Entity
@Table(name = "customer")
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;

    private String name;

    @Enumerated(EnumType.STRING)
    private CustomerType type;

    /** Null for an unregistered (B2C) customer. */
    private String gstin;

    @Column(name = "state_code")
    private String stateCode;

    private String address;

    private String phone;

    /** The salesman who looks after this customer (R2.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salesman_id")
    private AppUser salesman;

    private boolean active;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** R2.2: is this customer assigned to the given user? */
    public boolean isAssignedTo(AppUser user) {
        return salesman != null && salesman.getId().equals(user.getId());
    }
}
