package com.docforge;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for integration tests.
 * Uses the running docker-compose containers (postgres:5432, mongo:27017, redis:6379).
 * Start the stack first: docker-compose up -d
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIT {
}
