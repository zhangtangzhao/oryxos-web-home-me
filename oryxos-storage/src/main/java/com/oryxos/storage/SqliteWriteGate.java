package com.oryxos.storage;

import java.util.function.Supplier;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 单节点 SQLite 单写者拓扑的进程内闸门：同一时刻至多一个写事务触达引擎。
 *
 * SQLite 同一时刻仅允许一个写者；并发 BEGIN IMMEDIATE 会落入引擎默认忙等
 * 退避（1/2/5/10/…/100ms 步进），突发并发下形成锁车队，尾延迟放大到数百毫秒。
 * 写者在进程内先排队（ReentrantLock 挂起，虚拟线程不钉住载体线程），从根上
 * 消除忙等；读路径不受影响（WAL 读写并发）。
 *
 * 必须用 ReentrantLock 而非 synchronized：JDK 21 虚拟线程在 synchronized
 * 块上会钉住载体线程，等待写锁期间会饿死同机别的虚拟线程（含读请求）。
 */
public final class SqliteWriteGate {

    private static final ReentrantLock LOCK = new ReentrantLock();

    private SqliteWriteGate() {
    }

    public static <T> T write(Supplier<T> work) {
        LOCK.lock();
        try {
            return work.get();
        } finally {
            LOCK.unlock();
        }
    }

    public static void write(Runnable work) {
        LOCK.lock();
        try {
            work.run();
        } finally {
            LOCK.unlock();
        }
    }
}
