package com.vgrid.dao.jdbc;

import com.vgrid.dao.ConflictException;
import com.vgrid.dao.DataAccessException;
import com.vgrid.dao.UserDao;
import com.vgrid.model.User;

import java.sql.*;
import java.util.Optional;

/** The users table. */
public class JdbcUserDao implements UserDao {
    private final Database db;

    public JdbcUserDao(Database db) {
        this.db = db;
    }

    @Override
    public Optional<Account> findAccount(String email) {
        String sql = "SELECT id, name, email, password_hash FROM users WHERE email = ?";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                User user = new User(rs.getInt("id"), rs.getString("name"), rs.getString("email"));
                return Optional.of(new Account(user, rs.getString("password_hash")));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not look up the account", e);
        }
    }

    @Override
    public User createUser(String name, String email, String passwordHash) {
        String sql = "INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, passwordHash);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new User(keys.getInt(1), name, email);
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new ConflictException("An account with this email already exists.");
        } catch (SQLException e) {
            throw new DataAccessException("Could not create the account", e);
        }
    }
}
