package com.garage.billing.repository;

import com.garage.billing.model.Customer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class CustomerRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final RowMapper<Customer> CUSTOMER_MAPPER = (rs, rowNum) -> {

        Customer c = new Customer();

        c.setId(rs.getLong("id"));
        c.setName(rs.getString("name"));
        c.setPhone(rs.getString("phone"));
        c.setEmail(rs.getString("email"));

        Timestamp ts = rs.getTimestamp("created_at");

        if (ts != null) {
            c.setCreatedAt(ts.toLocalDateTime());
        }

        return c;
    };

    // SAVE CUSTOMER
    public Long save(Customer customer) {

        String sql =
                "INSERT INTO customers (name, phone, email) VALUES (?, ?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(conn -> {

            PreparedStatement ps =
                    conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

            ps.setString(1, customer.getName());
            ps.setString(2, customer.getPhone());
            ps.setString(3, customer.getEmail());

            return ps;

        }, keyHolder);

        return keyHolder.getKey().longValue();
    }

    // FIND BY ID
    public Optional<Customer> findById(Long id) {

        String sql = "SELECT * FROM customers WHERE id = ?";

        List<Customer> results =
                jdbcTemplate.query(sql, CUSTOMER_MAPPER, id);

        return results.stream().findFirst();
    }

    // FIND BY PHONE
    public Optional<Customer> findByPhone(String phone) {

        String sql = "SELECT * FROM customers WHERE phone = ?";

        List<Customer> results =
                jdbcTemplate.query(sql, CUSTOMER_MAPPER, phone);

        return results.stream().findFirst();
    }

    // FIND ALL
    public List<Customer> findAll() {

        String sql =
                "SELECT * FROM customers ORDER BY name ASC";

        return jdbcTemplate.query(sql, CUSTOMER_MAPPER);
    }

    // SEARCH BY NAME
    public List<Customer> searchByName(String name) {

        String sql = """
            SELECT * FROM customers
            WHERE name LIKE ?
            ORDER BY name ASC
            """;

        String searchPattern = "%" + name.trim() + "%";

        return jdbcTemplate.query(
                sql,
                CUSTOMER_MAPPER,
                searchPattern
        );
    }

    // UPDATE CUSTOMER
    public int update(Customer customer) {

        String sql =
                "UPDATE customers SET name=?, phone=?, email=? WHERE id=?";

        return jdbcTemplate.update(
                sql,
                customer.getName(),
                customer.getPhone(),
                customer.getEmail(),
                customer.getId()
        );
    }

    // CHECK PHONE EXISTS
    public boolean existsByPhone(String phone) {

        String sql =
                "SELECT COUNT(*) FROM customers WHERE phone = ?";

        Integer count =
                jdbcTemplate.queryForObject(
                        sql,
                        Integer.class,
                        phone
                );

        return count != null && count > 0;
    }
}