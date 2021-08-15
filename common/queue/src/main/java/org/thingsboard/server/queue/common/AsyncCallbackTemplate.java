package org.thingsboard.server.queue.common;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Created by ashvayka on 05.10.18.
 */
public class AsyncCallbackTemplate {

    public static <T> void withCallbackAndTimeout(ListenableFuture<T> future,
                                                  Consumer<T> onSuccess,
                                                  Consumer<Throwable> onFailure,
                                                  long timeoutInMs,
                                                  ScheduledExecutorService timeoutExecutor,
                                                  Executor callbackExecutor) {
        future = Futures.withTimeout(future, timeoutInMs, TimeUnit.MILLISECONDS, timeoutExecutor);
        withCallback(future, onSuccess, onFailure, callbackExecutor);
    }

    /**
     *  <pre>
     *      作用：
     *          使用线程池executor, 执行 future。 如果成功执行onSuccess，如果失败执行onFailure
     *  </pre>
     *
     * @param future      异步任务
     * @param onSuccess   成功回调方法
     * @param onFailure   失败回调方法
     * @param executor    线程池
     */
    public static <T> void withCallback(ListenableFuture<T> future, Consumer<T> onSuccess,
                                        Consumer<Throwable> onFailure, Executor executor) {
        FutureCallback<T> callback = new FutureCallback<T>() {
            @Override
            public void onSuccess(T result) {
                try {
                    onSuccess.accept(result);
                } catch (Throwable th) {
                    onFailure(th);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                onFailure.accept(t);
            }
        };

        if (executor != null) {
            Futures.addCallback(future, callback, executor);
        } else {
            Futures.addCallback(future, callback, MoreExecutors.directExecutor());
        }
    }

}
