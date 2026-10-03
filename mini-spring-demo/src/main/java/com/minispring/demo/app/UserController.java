package com.minispring.demo.app;

import com.minispring.context.annotation.Autowired;
import com.minispring.jdbc.DuplicateKeyException;
import com.minispring.jdbc.JdbcTemplate;
import com.minispring.web.mvc.annotation.DeleteMapping;
import com.minispring.web.mvc.annotation.GetMapping;
import com.minispring.web.mvc.annotation.PathVariable;
import com.minispring.web.mvc.annotation.PostMapping;
import com.minispring.web.mvc.annotation.PutMapping;
import com.minispring.web.mvc.annotation.RequestBody;
import com.minispring.web.mvc.annotation.RequestMapping;
import com.minispring.web.mvc.annotation.RestController;
import com.minispring.web.servlet.ResponseStatusException;

import java.sql.SQLException;
import java.util.List;

/**
 * 用户接口：CRUD 全链路通过 {@code JdbcTemplate} 持久化到 MySQL，写入结果可由
 * {@code docker exec minispring-mysql mysql ...} 直接核验。
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final JdbcTemplate jdbc;

    @Autowired
    public UserController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/{id}")
    public User getUser(@PathVariable("id") Long id) {
        User user = jdbc.queryOne("SELECT id, name, email FROM users WHERE id = ?",
                UserController::mapUser, id);
        if (user == null) {
            // 资源不存在返回 404。
            throw new ResponseStatusException(404, "用户不存在: " + id);
        }
        return user;
    }

    @GetMapping
    public List<User> listUsers() {
        return jdbc.query("SELECT id, name, email FROM users ORDER BY id", UserController::mapUser);
    }

    @PostMapping
    public User createUser(@RequestBody User user) {
        try {
            long id = jdbc.insertAndReturnKey("INSERT INTO users(name, email) VALUES (?, ?)",
                    user.getName(), user.getEmail());
            user.setId(id);
            return user;
        } catch (DuplicateKeyException e) {
            throw duplicateEmailConflict(e);
        }
    }

    @PutMapping("/{id}")
    public int updateUser(@PathVariable("id") Long id, @RequestBody User user) {
        try {
            return jdbc.update("UPDATE users SET name = ?, email = ? WHERE id = ?",
                    user.getName(), user.getEmail(), id);
        } catch (DuplicateKeyException e) {
            throw duplicateEmailConflict(e);
        }
    }

    @DeleteMapping("/{id}")
    public int deleteUser(@PathVariable("id") Long id) {
        return jdbc.update("DELETE FROM users WHERE id = ?", id);
    }

    private static User mapUser(java.sql.ResultSet rs) throws java.sql.SQLException {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setName(rs.getString("name"));
        user.setEmail(rs.getString("email"));
        return user;
    }

    private static RuntimeException duplicateEmailConflict(DuplicateKeyException failure) {
        // DuplicateKeyException 也覆盖其它 SQLState 23 约束；本 demo 只把 MySQL 1062 稳定映射成邮箱冲突。
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (current instanceof SQLException sqlException && sqlException.getErrorCode() == 1062) {
                return new ResponseStatusException(409, "该邮箱已存在", failure);
            }
            Throwable next = current.getCause();
            if (next == current) {
                break;
            }
            current = next;
        }
        return failure;
    }
}
