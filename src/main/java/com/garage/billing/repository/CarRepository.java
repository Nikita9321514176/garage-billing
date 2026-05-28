package com.garage.billing.repository;

import com.garage.billing.model.Car;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.SQLException;

import java.util.List;
import java.util.Optional;

@Repository
public class CarRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ─────────────────────────────────────────────────────────
    // ROW MAPPER
    // Converts one database row into one Car object
    // ─────────────────────────────────────────────────────────
    private static final RowMapper<Car> CAR_MAPPER = (rs, rowNum) -> {

        Car car = new Car();

        car.setId(rs.getLong("id"));
        car.setCustomerId(rs.getLong("customer_id"));
        car.setCarNumber(rs.getString("car_number"));
        car.setCarModel(rs.getString("car_model"));
        car.setBrand(rs.getString("brand"));
        car.setColor(rs.getString("color"));

        // manufacture_year can be NULL
        int year = rs.getInt("manufacture_year");

        if (rs.wasNull()) {
            car.setManufactureYear(null);
        } else {
            car.setManufactureYear(year);
        }

        Timestamp ts = rs.getTimestamp("created_at");

        if (ts != null) {
            car.setCreatedAt(ts.toLocalDateTime());
        }

        // customer_name exists only in JOIN queries
        try {
            car.setCustomerName(rs.getString("customer_name"));
        } catch (SQLException ignored) {
        }

        return car;
    };

    // ─────────────────────────────────────────────────────────
    // SAVE NEW CAR
    // ─────────────────────────────────────────────────────────
    public Long save(Car car) {

        String sql = """
                INSERT INTO cars
                (
                    customer_id,
                    car_number,
                    car_model,
                    brand,
                    manufacture_year,
                    color
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(conn -> {

            PreparedStatement ps =
                    conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

            ps.setLong(1, car.getCustomerId());
            ps.setString(2, car.getCarNumber());
            ps.setString(3, car.getCarModel());
            ps.setString(4, car.getBrand());

            // Handles NULL safely
            ps.setObject(5, car.getManufactureYear());

            ps.setString(6, car.getColor());

            return ps;

        }, keyHolder);

        return keyHolder.getKey().longValue();
    }

    // ─────────────────────────────────────────────────────────
    // FIND CAR BY ID
    // ─────────────────────────────────────────────────────────
    public Optional<Car> findById(Long id) {

        String sql =
                "SELECT * FROM cars WHERE id = ?";

        List<Car> results =
                jdbcTemplate.query(sql, CAR_MAPPER, id);

        return results.stream().findFirst();
    }

    // ─────────────────────────────────────────────────────────
    // FIND CAR BY NUMBER
    // ─────────────────────────────────────────────────────────
    public Optional<Car> findByCarNumber(String carNumber) {

        String sql = """
                SELECT c.*, cu.name AS customer_name
                FROM cars c
                JOIN customers cu
                    ON c.customer_id = cu.id
                WHERE c.car_number = ?
                """;

        List<Car> results =
                jdbcTemplate.query(sql, CAR_MAPPER, carNumber);

        return results.stream().findFirst();
    }

    // ─────────────────────────────────────────────────────────
    // GET ALL CARS FOR ONE CUSTOMER
    // ─────────────────────────────────────────────────────────
    public List<Car> findByCustomerId(Long customerId) {

        String sql =
                "SELECT * FROM cars WHERE customer_id = ? ORDER BY created_at DESC";

        return jdbcTemplate.query(sql, CAR_MAPPER, customerId);
    }

    // ─────────────────────────────────────────────────────────
    // CHECK IF CAR NUMBER EXISTS
    // ─────────────────────────────────────────────────────────
    public boolean existsByCarNumber(String carNumber) {

        String sql =
                "SELECT COUNT(*) FROM cars WHERE car_number = ?";

        Integer count =
                jdbcTemplate.queryForObject(sql, Integer.class, carNumber);

        return count != null && count > 0;
    }
}