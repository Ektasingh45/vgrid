package com.vgrid.service;

import com.vgrid.dao.UserDao;
import com.vgrid.model.User;
import com.vgrid.util.PasswordHasher;

import java.util.Locale;
import java.util.regex.Pattern;

/** Sign-in and sign-up. Throws IllegalArgumentException with a message the form can show. */
public class AuthService {
    public static final int MIN_PASSWORD_LENGTH = 6;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserDao users;

    public AuthService(UserDao users) {
        this.users = users;
    }

    public User signIn(String email, String password) {
        String cleanEmail = normalise(email);
        if (cleanEmail.isEmpty() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Please enter your email and password.");
        }
        // Same message for "no such email" and "wrong password", so nobody can find out which emails exist.
        return users.findAccount(cleanEmail)
                .filter(account -> PasswordHasher.matches(password, account.passwordHash()))
                .map(UserDao.Account::user)
                .orElseThrow(() -> new IllegalArgumentException("Incorrect email or password."));
    }

    public User register(String name, String email, String password, String confirmPassword) {
        String cleanName = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        String cleanEmail = normalise(email);
        if (cleanName.isEmpty()) {
            throw new IllegalArgumentException("Please enter your name.");
        }
        if (cleanName.length() > 100) {
            throw new IllegalArgumentException("Please use a shorter name (100 characters at most).");
        }
        if (!EMAIL.matcher(cleanEmail).matches() || cleanEmail.length() > 150) {
            throw new IllegalArgumentException("Please enter a valid email address.");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Your password needs at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (!password.equals(confirmPassword)) {
            throw new IllegalArgumentException("The two passwords do not match.");
        }
        if (users.findAccount(cleanEmail).isPresent()) {
            throw new IllegalArgumentException("An account with this email already exists. Please sign in.");
        }
        return users.createUser(cleanName, cleanEmail, PasswordHasher.hash(password));
    }

    private static String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
