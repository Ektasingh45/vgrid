package com.vgrid.dao;

import com.vgrid.model.User;

import java.util.Optional;

/** Reads and creates user accounts. */
public interface UserDao {

    /** A stored account: the user plus the password hash used to check the login. */
    record Account(User user, String passwordHash) {
    }

    /** Looks up an account by email (already trimmed and lower-case). */
    Optional<Account> findAccount(String email);

    /** Creates an account. Throws ConflictException when the email is already registered. */
    User createUser(String name, String email, String passwordHash);
}
