/*
 * Copyright (C) 2012-2020 Markus Junginger, greenrobot (http://greenrobot.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.greenrobot.eventbus;

import android.os.Looper;

import org.greenrobot.eventbus.android.AndroidLogger;
import org.greenrobot.eventbus.meta.SubscriberInfoIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Creates EventBus instances with custom parameters and also allows to install a custom default EventBus instance.
 * Create a new builder using {@link EventBus#builder()}.
 */
@SuppressWarnings("unused")
public class EventBusBuilder {
    private final static ExecutorService DEFAULT_EXECUTOR_SERVICE = Executors.newCachedThreadPool();

    boolean logSubscriberExceptions = true;
    boolean logNoSubscriberMessages = true;
    boolean sendSubscriberExceptionEvent = true;
    boolean sendNoSubscriberEvent = true;
    boolean throwSubscriberException;
    //事件是否分发到超类型
    boolean eventInheritance = true;
    boolean ignoreGeneratedIndex;
    boolean strictMethodVerification;
    ExecutorService executorService = DEFAULT_EXECUTOR_SERVICE;
    List<Class<?>> skipMethodVerificationForClasses;
    //注释预处理器生成的索引，自定义如何查找订阅方法
    List<SubscriberInfoIndex> subscriberInfoIndexes;
    Logger logger;
    MainThreadSupport mainThreadSupport;

    EventBusBuilder() {
    }

    /** Default: true */
    public EventBusBuilder logSubscriberExceptions(boolean logSubscriberExceptions) {
        this.logSubscriberExceptions = logSubscriberExceptions;
        return this;
    }

    /** Default: true */
    public EventBusBuilder logNoSubscriberMessages(boolean logNoSubscriberMessages) {
        this.logNoSubscriberMessages = logNoSubscriberMessages;
        return this;
    }

    /** Default: true */
    public EventBusBuilder sendSubscriberExceptionEvent(boolean sendSubscriberExceptionEvent) {
        this.sendSubscriberExceptionEvent = sendSubscriberExceptionEvent;
        return this;
    }

    /** Default: true */
    public EventBusBuilder sendNoSubscriberEvent(boolean sendNoSubscriberEvent) {
        this.sendNoSubscriberEvent = sendNoSubscriberEvent;
        return this;
    }

    /**
     * Fails if an subscriber throws an exception (default: false).
     * <p/>
     * Tip: Use this with BuildConfig.DEBUG to let the app crash in DEBUG mode (only). This way, you won't miss
     * exceptions during development.
     */
    public EventBusBuilder throwSubscriberException(boolean throwSubscriberException) {
        this.throwSubscriberException = throwSubscriberException;
        return this;
    }

    /**
     * 默认情况下，EventBus考虑event class层次结构(将通知subscribers的超类)。关闭此功能将改善事件的发布。对于
     * 直接扩展对象的简单事件类，我们测量到事件发布的速度提高了20%。对于更复杂的事件层次结构，加速应该大于20%。
     * <p/>
     * 但是，请记住，在一个应用程序中，事件发布通常只消耗一小部分CPU时间，除非它以很高的速率发布，比如每秒数百/数千
     * 个事件。
     */
    public EventBusBuilder eventInheritance(boolean eventInheritance) {
        this.eventInheritance = eventInheritance;
        return this;
    }


    /**
     * Provide a custom thread pool to EventBus used for async and background event delivery. This is an advanced
     * setting to that can break things: ensure the given ExecutorService won't get stuck to avoid undefined behavior.
     */
    public EventBusBuilder executorService(ExecutorService executorService) {
        this.executorService = executorService;
        return this;
    }

    /**
     * Method name verification is done for methods starting with onEvent to avoid typos; using this method you can
     * exclude subscriber classes from this check. Also disables checks for method modifiers (public, not static nor
     * abstract).
     */
    public EventBusBuilder skipMethodVerificationFor(Class<?> clazz) {
        if (skipMethodVerificationForClasses == null) {
            skipMethodVerificationForClasses = new ArrayList<>();
        }
        skipMethodVerificationForClasses.add(clazz);
        return this;
    }

    /** 强制使用反射，即使有一个生成的索引(默认:false)。*/
    public EventBusBuilder ignoreGeneratedIndex(boolean ignoreGeneratedIndex) {
        this.ignoreGeneratedIndex = ignoreGeneratedIndex;
        return this;
    }

    /** Enables strict method verification (default: false). */
    public EventBusBuilder strictMethodVerification(boolean strictMethodVerification) {
        this.strictMethodVerification = strictMethodVerification;
        return this;
    }

    /** 添加一个由EventBus的注释预处理器生成的索引。*/
    public EventBusBuilder addIndex(SubscriberInfoIndex index) {
        if (subscriberInfoIndexes == null) {
            subscriberInfoIndexes = new ArrayList<>();
        }
        subscriberInfoIndexes.add(index);
        return this;
    }

    /**
     * Set a specific log handler for all EventBus logging.
     * <p/>
     * By default all logging is via {@link android.util.Log} but if you want to use EventBus
     * outside the Android environment then you will need to provide another log target.
     */
    public EventBusBuilder logger(Logger logger) {
        this.logger = logger;
        return this;
    }

    Logger getLogger() {
        if (logger != null) {
            return logger;
        } else {
            return Logger.Default.get();
        }
    }

    MainThreadSupport getMainThreadSupport() {
        if (mainThreadSupport != null) {
            return mainThreadSupport;
        } else if (AndroidLogger.isAndroidLogAvailable()) {
            Object looperOrNull = getAndroidMainLooperOrNull();
            return looperOrNull == null ? null :
                    new MainThreadSupport.AndroidHandlerMainThreadSupport((Looper) looperOrNull);
        } else {
            return null;
        }
    }

    static Object getAndroidMainLooperOrNull() {
        try {
            return Looper.getMainLooper();
        } catch (RuntimeException e) {
            // Not really a functional Android (e.g. "Stub!" maven dependencies)
            return null;
        }
    }

    /**
     * Installs the default EventBus returned by {@link EventBus#getDefault()} using this builders' values. Must be
     * done only once before the first usage of the default EventBus.
     *
     * @throws EventBusException if there's already a default EventBus instance in place
     */
    public EventBus installDefaultEventBus() {
        synchronized (EventBus.class) {
            if (EventBus.defaultInstance != null) {
                throw new EventBusException("Default instance already exists." +
                        " It may be only set once before it's used the first time to ensure consistent behavior.");
            }
            EventBus.defaultInstance = build();
            return EventBus.defaultInstance;
        }
    }

    /** Builds an EventBus based on the current configuration. */
    public EventBus build() {
        return new EventBus(this);
    }

}
