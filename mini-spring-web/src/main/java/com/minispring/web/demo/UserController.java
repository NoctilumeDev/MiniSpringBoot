package com.minispring.web.demo;

import com.minispring.web.mvc.annotation.GetMapping;
import com.minispring.web.mvc.annotation.PathVariable;
import com.minispring.web.mvc.annotation.PostMapping;
import com.minispring.web.mvc.annotation.RequestBody;
import com.minispring.web.mvc.annotation.RequestMapping;
import com.minispring.web.mvc.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用户接口：演示路径参数绑定、@RequestBody 反序列化、@ResponseBody 序列化。
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final Map<Long, User> store = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    public UserController() {
        User alice = new User();
        alice.setId(1L);
        alice.setName("Alice");
        alice.setEmail("alice@example.com");
        store.put(1L, alice);
    }

    @GetMapping("/{id}")
    public User getUser(@PathVariable("id") Long id) {
        User user = store.get(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在: " + id);
        }
        return user;
    }

    @PostMapping
    public User createUser(@RequestBody User user) {
        long id = sequence.incrementAndGet() + 1; // +1 让首个新建用户从 id=2 开始，避开预置数据
        user.setId(id);
        store.put(id, user);
        return user;
    }
}