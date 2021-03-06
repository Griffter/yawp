package io.yawp.driver.postgresql.sql;

import java.sql.Connection;

public class ConnectionManager {

    private ConnectionPool connectionPool;

    private Connection connection;

    public ConnectionManager() {
        this.connectionPool = new ConnectionPool();
    }

    public ConnectionManager(String dataSourceName) {
        this.connectionPool = new ConnectionPool(dataSourceName);
    }

    private Connection getConnection() {
        if (isTransactionInProgress()) {
            return connection;
        }
        return connectionPool.connection();
    }

    private void returnToPool(Connection connection) {
        connectionPool.close(connection);
    }

    private boolean isTransactionInProgress() {
        return this.connection != null;
    }

    public <T> T executeQuery(SqlRunner runner) {
        Connection connection = getConnection();
        try {
            return runner.executeQuery(connection);
        } finally {
            if (!isTransactionInProgress()) {
                returnToPool(connection);
            }
        }
    }

    public void execute(SqlRunner runner) {
        Connection connection = getConnection();
        try {
            runner.execute(connection);
        } finally {
            if (!isTransactionInProgress()) {
                returnToPool(connection);
            }
        }
    }

    public void execute(String sql) {
        execute(new SqlRunner(sql));
    }

    public synchronized void beginTransaction() {
        if (isTransactionInProgress()) {
            throw new RuntimeException("Another transaction already in progress");
        }

        this.connection = connectionPool.connection(false);
    }

    public synchronized void rollback() {
        if (!isTransactionInProgress()) {
            throw new RuntimeException("No transaction already in progress");
        }
        connectionPool.rollbackAndClose(connection);
        this.connection = null;
    }

    public synchronized void commit() {
        if (!isTransactionInProgress()) {
            throw new RuntimeException("No transaction already in progress");
        }
        connectionPool.commitAndClose(connection);
        this.connection = null;
    }

}
