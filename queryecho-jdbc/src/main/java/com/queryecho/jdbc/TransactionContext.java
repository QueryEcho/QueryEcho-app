package com.queryecho.jdbc;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/** 현재 스레드의 물리 트랜잭션 ID를 JDBC 계측 코드에 전달한다. */
public final class TransactionContext {
    private static final ThreadLocal<Deque<UUID>> CURRENT = ThreadLocal.withInitial(ArrayDeque::new);

    private TransactionContext() {
    }

    public static void push(UUID transactionId) {
        CURRENT.get().push(transactionId);
    }

    public static UUID currentId() {
        return CURRENT.get().peek();
    }

    public static void remove(UUID transactionId) {
        Deque<UUID> stack = CURRENT.get();
        if (!stack.isEmpty() && transactionId.equals(stack.peek())) {
            stack.pop();
        } else {
            stack.removeFirstOccurrence(transactionId);
        }
        if (stack.isEmpty()) {
            CURRENT.remove();
        }
    }
}
