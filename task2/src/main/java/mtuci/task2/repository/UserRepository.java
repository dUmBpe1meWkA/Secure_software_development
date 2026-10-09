package mtuci.task2.repository;

import mtuci.task2.model.User;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository extends InMemoryRepository<User> {}
