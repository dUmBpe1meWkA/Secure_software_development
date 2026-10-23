package mtuci.task2.repository;

import java.util.*;
import java.util.function.Function;
import mtuci.task2.model.Entity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;


public abstract class JdbcRepository<T extends Entity> {
    private final JdbcTemplate jdbc;
    private final String table;
    private final String[] columns;
    private final RowMapper<T> mapper;
    private final Function<T, Object[]> values;

    protected JdbcRepository(JdbcTemplate jdbc, String table, String columns,
            RowMapper<T> mapper, Function<T, Object[]> values) {
        this.jdbc = jdbc;
        this.table = table;
        this.columns = columns.split(",");
        this.mapper = mapper;
        this.values = values;
    }
    public long nextId() {
        return jdbc.queryForObject("SELECT nextval('" + table + "_seq')", Long.class);
    }
    public List<T> findAll() { return jdbc.query("SELECT * FROM " + table + " ORDER BY id", mapper); }
    public Optional<T> findById(Long id) {
        return jdbc.query("SELECT * FROM " + table + " WHERE id = ?", mapper, id).stream().findFirst();
    }
    public T save(T entity) {
        Object[] data = values.apply(entity);
        Object[] update = Arrays.copyOf(data, data.length + 1);
        update[data.length] = entity.id();
        String assignments = String.join(",", Arrays.stream(columns).map(c -> c + "=?").toList());
        if (jdbc.update("UPDATE " + table + " SET " + assignments + " WHERE id=?", update) == 0) {
            Object[] insert = new Object[data.length + 1];
            insert[0] = entity.id();
            System.arraycopy(data, 0, insert, 1, data.length);
            jdbc.update("INSERT INTO " + table + " (id," + String.join(",", columns) + ") VALUES ("
                    + String.join(",", Collections.nCopies(insert.length, "?")) + ")", insert);
        }
        return entity;
    }
    public void deleteById(Long id) { jdbc.update("DELETE FROM " + table + " WHERE id=?", id); }
}
