# JedisMessaging

**JedisMessaging** is a messaging system built on top of Redis, utilizing the [Jedis](https://github.com/redis/jedis) library. It provides robust mechanisms to publish and subscribe to messages, handle callbacks, and manage listeners for specific events or patterns.

## Features

- **Publish/Subscribe**: Seamless publishing and subscribing to channels.
- **Callback Handling**: Support for callbacks that can be triggered on message reception.
- **Listener Management**: Manage listeners for specific events or patterns.
- **Thread Safety**: Utilizes multi-threading for efficient message handling.
- **JSON Serialization/Deserialization**: Integrated with Gson for easy JSON handling.
- **Expiration Management**: Automatic cleanup of expired callbacks.

## [Installation](https://jitpack.io/#ClydoNetwork/JedisMessaging)

## Usage

### 1. Initialization

To use JedisMessaging, you need to initialize it with a `JedisPool` and `Gson` instance:

```java
JedisPool jedisPool = new JedisPool("localhost", 6379);
Gson gson = new Gson();

JedisMessaging jedisMessaging = new JedisMessaging(jedisPool, gson);
```

You can also specify the callback expiration time (in seconds) during initialization:

```java
JedisMessaging jedisMessaging = new JedisMessaging(jedisPool, gson, 30);
```

### 2. Publishing Messages

#### `publish(String channel, String event, Object message, ReceiveCallback receiveCallback, boolean skipSelf)`

Publishes a message to a specified channel with an optional callback and the option to skip the sender.

```java
jedisMessaging.publish("channel1", "event1", "Hello, World!", (channel, json) -> {
    System.out.println("Received response: " + channel + ", " + json);
}, false);
```

#### `publish(String channel, String event, Object message, boolean skipSelf)`

Publishes a message to a specified channel without a callback.

```java
jedisMessaging.publish("channel1", "event1", "Hello, World!", false);
```

#### `publish(String event, Object message, ReceiveCallback receiveCallback, boolean skipSelf)`

Publishes a message to the default channel with an optional callback and the option to skip the sender.

```java
jedisMessaging.setDefaultPublishChannel("defaultChannel");
jedisMessaging.publish("event1", "Hello to default channel", (channel, json) -> {
    System.out.println("Received response: " + channel + ", " + json);
}, false);
```

#### `publish(String event, Object message, boolean skipSelf)`

Publishes a message to the default channel without a callback.

```java
jedisMessaging.publish("event1", "Hello to default channel", false);
```

### 3. Subscribing to Channels

#### `subscribe(Listener listener)`

Subscribes a listener to events or patterns as defined by the `JedisChannels` and `JedisEvent` annotations.

```java
@JedisChannels("channel1")
@JedisEvent("event1")
public class MyListener implements Listener {
    public void call(String channel, JsonElement json, SendCallback sender) {
        System.out.println("Received message");
    }
}

MyListener listener = new MyListener();
jedisMessaging.subscribe(listener);
```

#### `<L> subscribeFrom(L listener)`

Subscribes a listener to events or patterns as defined by the `JedisChannels` and `JedisEvent` annotations.

```java
@JedisChannels("channel1")
public class MyListener {
    @JedisEvent("event1")
    public void onEvent1(String channel, JsonElement json, SendCallback sender) {
        System.out.println("Received message");
    }

    @JedisEvent("event2")
    public void onEvent2(String channel, JsonElement json, SendCallback sender) {
        System.out.println("Received message");
    }
}

MyListener listener = new MyListener();
jedisMessaging.subscribeFrom(listener);
```

### 4. Closing the Connection

#### `close()`

Closes the `JedisMessaging` instance, shutting down executors and closing the Jedis pool.

```java
jedisMessaging.close();
```

## Configuration

- **`callbacksExpiresIn`**: Specifies the time in seconds after which callbacks will expire and be cleaned up. Default is `20` seconds.
- **`defaultPublishChannel`**: The default channel to which messages will be published if no specific channel is provided.