/*
 * Copyright (C) 2012-2016 Markus Junginger, greenrobot (http://greenrobot.org)
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

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

public class HandlerPoster extends Handler implements Poster {
    //待发送事件队列
    private final PendingPostQueue queue;
    private final int maxMillisInsideHandleMessage;
    private final EventBus eventBus;
    private boolean handlerActive;

    protected HandlerPoster(EventBus eventBus, Looper looper, int maxMillisInsideHandleMessage) {
        //传入的是主线程的Looper对象，所以这个Handler对象是主线程的Handler
        super(looper);
        this.eventBus = eventBus;
        this.maxMillisInsideHandleMessage = maxMillisInsideHandleMessage;
        //创建一个事件队列
        queue = new PendingPostQueue();
    }

    public void enqueue(Subscription subscription, Object event) {
        ////根据传入的参数，从缓存池获复用或创建pendingPost对象
        PendingPost pendingPost = PendingPost.obtainPendingPost(subscription, event);
        synchronized (this) {
            //将pendingPost加入到队列中
            queue.enqueue(pendingPost);
            if (!handlerActive) {
                handlerActive = true;
                //子线程往主线程发消息，通知主线程通过handleMessage处理
                if (!sendMessage(obtainMessage())) {
                    throw new EventBusException("Could not send handler message");
                }
            }
        }
    }

    @Override
    public void handleMessage(Message msg) {
        boolean rescheduled = false;
        try {
            long started = SystemClock.uptimeMillis();
            //无限循环处理待处理的消息
            while (true) {
                //从消息队列中获取要处理的事件
                PendingPost pendingPost = queue.poll();
                //进行两次检查，确保pendingPost不为null，如果为null直接跳出循环
                if (pendingPost == null) {
                    synchronized (this) {
                        // Check again, this time in synchronized
                        pendingPost = queue.poll();
                        if (pendingPost == null) {
                            handlerActive = false;
                            return;
                        }
                    }
                }
                //使用反射的方法调用订阅者的订阅方法。
                eventBus.invokeSubscriber(pendingPost);
                long timeInMethod = SystemClock.uptimeMillis() - started;
                if (timeInMethod >= maxMillisInsideHandleMessage) {
                    if (!sendMessage(obtainMessage())) {
                        throw new EventBusException("Could not send handler message");
                    }
                    rescheduled = true;
                    return;
                }
            }
        } finally {
            handlerActive = rescheduled;
        }
    }
}