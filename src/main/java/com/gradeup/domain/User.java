package com.gradeup.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A student who has signed up. Their email address is what they log in with.
 *
 * HOW DATABASE CLASSES ("ENTITIES") WORK
 * Each class in this "domain" folder is saved as a table in the database.
 * Each field becomes a column, and each object becomes one row.
 * We never write SQL to create these tables — Hibernate (part of Spring Data JPA) reads
 * the annotations below and creates/updates the tables for us when the app starts.
 *
 *   @Entity          - "save objects of this class in the database"
 *   @Table           - the table's name, plus rules (here: no two users can share an email)
 *   @Id              - this field is the unique id (primary key) of each row
 *   @GeneratedValue  - the database picks the id for us: 1, 2, 3...
 *   @Column          - settings for one column (is it required? how long can it be?)
 *   @OneToMany       - "one user has many courses" (see the courses field)
 *   @Transient       - "this is NOT a column", it's just calculated in Java
 */
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 190)
    private String email;

    /**
     * The password, scrambled with BCrypt. We NEVER store the real password —
     * at login, Spring Security scrambles what was typed and compares the two results.
     */
    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 80)
    private String displayName;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /**
     * All courses that belong to this user.
     * mappedBy = "owner"     -> the link is stored in Course's "owner" column.
     * cascade = ALL          -> saving/deleting the user also saves/deletes their courses.
     * orphanRemoval = true   -> a course removed from this list is deleted from the database.
     */
    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Course> courses = new ArrayList<>();

    /** Empty constructor that Hibernate needs. "protected" so our own code doesn't use it. */
    protected User() { }

    public User(String email, String passwordHash, String displayName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    /**
     * The user's initials, shown in the round avatar at the top right.
     * Example: "Ama Mensah" becomes "AM", and "Kofi" becomes "K".
     */
    @Transient
    public String getInitials() {
        if (displayName == null || displayName.isBlank()) {
            return "?";
        }

        // Split the name on spaces: "Ama Kesewa Mensah" -> ["Ama", "Kesewa", "Mensah"]
        String[] parts = displayName.trim().split("\\s+");

        String firstName = parts[0];
        String initials = String.valueOf(Character.toUpperCase(firstName.charAt(0)));

        // If there's more than one name, add the first letter of the LAST one.
        if (parts.length > 1) {
            String lastName = parts[parts.length - 1];
            initials = initials + Character.toUpperCase(lastName.charAt(0));
        }
        return initials;
    }

    // ----- Getters and setters (plain methods to read and change the fields) -----

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public List<Course> getCourses() { return courses; }
    public void setCourses(List<Course> courses) { this.courses = courses; }
}
