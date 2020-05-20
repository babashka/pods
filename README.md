# babashka.pods

This is the library to load babashka pods. It is used by
[babashka](https://github.com/borkdude/babashka/) but also usable from the JVM
and [sci](https://github.com/borkdude/sci)-based projects other than babashka.

## Introduction

Pods are standalone programs that can expose namespaces with vars to babashka or
a JVM. Pods can be built in Clojure, but also in languages that don't run on the
JVM.

Some terminology:

- _pod_: a program that exposes namespaces with vars via the _pod protocol_.
- _pod client_: the program invoking a pod. When babashka invokes a pod,
babashka is the pod client. When a JVM invokes a pod, the JVM is the pod client.
- _message_: a message sent from the pod client to the pod or vice versa,
  encoded in [bencode](https://en.wikipedia.org/wiki/Bencode) format.
- _payload_: a particular field of a _message_ encoded in a _payload format_
  (currently only JSON or EDN). Examples are `args`, `value` and `ex-data`.  _
-  _pod protocol_: the documented way of exchanging messages between a _pod
  client_ and _pod_.

Pods can be created independently from pod clients. Any program can be invoked
as a pod as long as it implements the _pod protocol_. This protocol is
influenced by and built upon battle-tested technologies:

- the [nREPL](https://nrepl.org/) and [LSP](https://microsoft.github.io/language-server-protocol/) protocols
- [bencode](https://en.wikipedia.org/wiki/Bencode)
- [JSON](https://www.json.org/json-en.html)
- [EDN](https://github.com/edn-format/edn)
- composition of UNIX command line tools in via good old stdin and stdout

Currently the following pods are available:

- [clj-kondo](https://github.com/borkdude/clj-kondo/#babashka-pod): a Clojure
  linter
- [pod-babashka-filewatcher](https://github.com/babashka/pod-babashka-filewatcher): a
  filewatcher pod based on Rust notify.
- [pod-babashka-hsqldb](https://github.com/babashka/pod-babashka-hsqldb): a pod
  that allows you to create and fire queries at a
  [HSQLDB](http://www.hsqldb.org/) database.
- [pod-jaydeesimon-jsoup](https://github.com/jaydeesimon/pod-jaydeesimon-jsoup):
    a pod for parsing HTML using CSS queries backed by Jsoup.
- [pod-lispyclouds-docker](https://github.com/lispyclouds/pod-lispyclouds-docker):
  A pod for interacting with docker

The name pod is inspired by [boot's pod
feature](https://github.com/boot-clj/boot/wiki/Pods). It means _underneath_ or
_below_ in Polish and Russian. In Romanian it means _bridge_
([source](https://en.wiktionary.org/wiki/pod)).

## Status

The protocol should be considered alpha. Breaking changes may occur at this
phase and will be documented in `CHANGELOG.md`.

## Usage

Using [pod-babashka-hsqldb](https://github.com/borkdude/pod-babashka-hsqldb) as
an example pod.

On the JVM:

``` clojure
(require '[babashka.pods :as pods])
(pods/load-pod "pod-babashka-hsqldb")
(require '[pod.babashka.hsqldb :as sql])

(def db "jdbc:hsqldb:mem:testdb;sql.syntax_mys=true")
(sql/execute! db ["create table foo ( foo int );"])
;;=> [#:next.jdbc{:update-count 0}]
```

## Sci

To use pods in a [sci](https://github.com/borkdude/sci) based project, see
[test/babashka/pods/sci_test.clj](test/babashka/pods/sci_test.clj).

## Why JVM support?

- Babashka pods allow you to leverage functionality from other programs
regardless of the technology they were implemented in. As such, pods can be a
light weight replacement for native interop (JNI, JNA, etc.).

- When developing pods, this library can be used to test them on the JVM.

## Implementing your own pod

### Examples

Beyond the already available pods mentioned above, eductional examples of pods
can be found [here](../examples/pods):

- [pod-lispyclouds-sqlite](../examples/pods/pod-lispyclouds-sqlite): a pod that
  allows you to create and fire queries at a [sqlite](https://www.sqlite.org/)
  database. Implemented in Python.

### Libraries

If you are looking for libraries to deal with bencode, JSON or EDN, take a look
at the existing pods or [nREPL](https://nrepl.org/nrepl/beyond_clojure.html)
implementations for various languages.

### Naming

When choosing a name for your pod, we suggest the following naming scheme:

```
pod-<user-id>-<pod-name>
```

where `<user-id>` is your Github or Gitlab handle and `<pod-name>` describes
what your pod is about.

Examples:

- [pod-lispyclouds-sqlite](../examples/pods/pod-lispyclouds-sqlite): a pod to
  communicate with [sqlite](https://www.sqlite.org/), provided by
  [@lispyclouds](https://github.com/lispyclouds).

Pods created by the babashka maintainers use the identifier `babashka`:

- [pod-babashka-hsqldb](https://github.com/borkdude/pod-babashka-hsqldb): a pod
  to communicate with [HSQLDB](http://www.hsqldb.org/)

### The protocol

#### Message and payload format

Exchange of _messages_ between pod client and the pod happens in the
[bencode](https://en.wikipedia.org/wiki/Bencode) format. Bencode is a bare-bones
format that only has four types:

- integers
- lists
- dictionaries (maps)
- byte strings

Additionally, _payloads_ like `args` (arguments) or `value` (a function return
value) are encoded in either JSON or EDN.

So remember: messages are in bencode, payloads (particular fields in the
message) are in either JSON or EDN.

Bencode is chosen as the message format because it is a light-weight format
which can be implemented in 200-300 lines of code in most languages. If pods are
implemented in Clojure, they only need to depend on the
[bencode](https://github.com/nrepl/bencode) library and use `pr-str` and
`edn/read-string` for encoding and decoding payloads.

Why isn't EDN or JSON chosen as the message format instead of bencode, you may
ask.  Assuming EDN or JSON as the message and payload format for all pods is too
constraining: other languages might already have built-in JSON support and there
might not be a good EDN library available. So we use bencode as the first
encoding and choose one of multiple richer encodings on top of this. More
payload formats might be added in the future (e.g. transit).

When calling the `babashka.pods/load-pod` function, the pod client will start
the pod and leave the pod running throughout the duration of a babashka script.

#### describe

The first message that the pod client will send to the pod on its stdin is:

``` clojure
{"op" "describe"}
```

Encoded in bencode this looks like:

``` clojure
(bencode/write-bencode System/out {"op" "describe"})
;;=> d2:op8:describee
```

The pod should reply to this request with a message in the vein of:

``` clojure
{"format" "json"
 "namespaces"
 [{"name" "pod.lispyclouds.sqlite"
   "vars" [{"name" "execute!"}]}]
 "ops" {"shutdown" {}}}
```

In this reply, the pod declares that payloads will be encoded and decoded using
JSON. It also declares that the pod exposes one namespace,
`pod.lispyclouds.sqlite` with one var `execute!`.

The pod encodes the above map to bencode and writes it to stdoud. The pod client
reads this message from the pod's stdout.

Upon receiving this message, the pod client creates these namespaces and vars.

The optional `ops` value communicates which ops the pod supports, beyond
`describe` and `invoke`. It is a map of op names to option maps. In the above
example the pod declares that it supports the `shutdown` op. Since the
`shutdown` op does not need any additional options right now, the value is an
empty map.

As a pod user, you can load the pod with:

``` clojure
(require '[babashka.pods :as pods])
(pods/load-pod "pod-lispyclouds-sqlite")
(some? (find-ns 'pod.lispyclouds.sqlite)) ;;=> true
;; yay, the namespace exists!

;; let's give the namespace an alias
(require '[pod.lispyclouds.sqlite :as sql])
```

#### invoke

When invoking a var that is related to the pod, let's call it a _proxy var_, the
pod client reaches out to the pod with the arguments encoded in JSON or EDN. The
pod will then respond with a return value encoded in JSON or EDN. The pod client
will then decode the return value and present the user with that.

Example: the user invokes `(sql/execute! "select * from foo")`. The pod client
sends this message to the pod:

``` clojure
{"id" "1d17f8fe-4f70-48bf-b6a9-dc004e52d056"
 "var" "pod.lispyclouds.sqlite/execute!"
 "args" "[\"select * from foo\"]"
```

The `id` is unique identifier generated by the pod client which correlates this
request with a response from the pod.

An example response from the pod could look like:

``` clojure
{"id" "1d17f8fe-4f70-48bf-b6a9-dc004e52d056"
 "value" "[[1] [2]]"
 "status" "[\"done\"]"}
```

Here, the `value` payload is the return value of the function invocation. The
field `status` contains `"done"`. This tells the pod client that this is the last
message related to the request with `id` `1d17f8fe-4f70-48bf-b6a9-dc004e52d056`.

Now you know most there is to know about the pod protocol!

#### shutdown

When the pod client is about to exit, it sends an `{"op" "shutdown"}` message, if the
pod has declared that it supports it in the `describe` response. Then it waits
for the pod process to end. This gives the pod a chance to clean up resources
before it exits. If the pod does not support the `shutdown` op, the pod process
is killed by the pod client.

#### out and err

Pods may send messages with an `out` and `err` string value. The Pod Client prints
these messages to `*out*` and `*err*`. Stderr from the pod is redirected to
`System/err`.

``` clojure
{"id" "1d17f8fe-4f70-48bf-b6a9-dc004e52d056"
 "out" "hello"}
```

``` clojure
{"id" "1d17f8fe-4f70-48bf-b6a9-dc004e52d056"
 "err" "debug"}
```

#### Error handling

Responses may contain an `ex-message` string and `ex-data` payload string (JSON
or EDN) along with an `"error"` value in `status`. This will cause the pod client to
throw an `ex-info` with the associated values.

Example:

``` clojure
{"id" "1d17f8fe-4f70-48bf-b6a9-dc004e52d056"
 "ex-message" "Illegal input"
 "ex-data" "{\"input\": 10}
 "status" "[\"done\", \"error\"]"}
```

#### Environment

The pod client will set the `BABASHKA_POD` environment variable to `true` when
invoking the pod. This can be used by the invoked program to determine whether
it should behave as a pod or not.

Added in v0.0.94.

#### Client side code

Pods may implement functions and macros by sending arbitrary code to the pod
client in a `"code"` field as part of a `"var"` section. The code is evaluated
by the pod client inside the declared namespace.

For example, a pod can define a macro called `do-twice`:

``` clojure
{"format" "json"
 "namespaces"
 [{"name" "pod.babashka.demo"
   "vars" [{"name" "do-twice" "code" "(defmacro do-twice [x] `(do ~x ~x))"}]}]}
```

In the pod client:

``` clojure
(pods/load-pod "pod-babashka-demo")
(require '[pod.babashka.demo :as demo])
(demo/do-twice (prn :foo))
;;=>
:foo
:foo
nil
```

#### Async

Asynchronous functions can be implemented using callbacks.

The pod will first declare a wrapper function accepting user provided callbacks
as client side code. An example from the
[filewatcher](https://github.com/babashka/pod-babashka-filewatcher) pod:

``` clojure
(defn watch
  ([path cb] (watch path cb {}))
  ([path cb opts]
   (babashka.pods/invoke
    "pod.babashka.filewatcher"
    'pod.babashka.filewatcher/watch*
    [path opts]
    {:handlers {:success (fn [event] (cb (update event :type keyword)))
                :error (fn [{:keys [:ex-message :ex-data]}]
                         (binding [*out* *err*]
                           (println "ERROR:" ex-message)))}})
   nil))
```

The wrapper function will then invoke `babashka.pods/invoke`, a lower level
function to invoke a pod var with callbacks.

The arguments to `babashka.pods/invoke` are:

- a pod identifier string derived from the first described namespace.
- the symbol of the var to invoke
- the arguments to the var
- an opts map containing `:handler` containing callback functions: `:success`, `:error` and `:done`

The return value of `babashka.pods/invoke` is a map containing `:result`. When
not using callbacks, this is the return value from the pod var invocation. When
using callbacks, this value is undefined.

The callback `:success` is called with a map containing a return value from the
pod invocation. The pod can potentially return multiple values. The callback
will be called with every value individually.

The callback `:error` is called in case the pod sends an error, a map
containing:

- `:ex-message`: an error message
- `:ex-data`: an arbitrary additional error data map. Typically it will contain
  `:type` describing the type of exception that happened in the pod.

If desired, `:ex-message` and `:ex-data` can be reified into a
`java.lang.Exception` using `ex-info`.

The callback `:done` is a 0-arg function. This callback can be used to determine
if the pod is done sending values, in case it wants to send multiple. The
callback is only called if no errors were sent by the pod.

In the above example the wrapper function calls the pod identified by
`"pod.babashka.filewatcher"`. It calls the var
`pod.babashka.filewatcher/watch*`. In `:on-success` it pulls out received
values, passing them to the user-provided callback. Additionally, it prints any
errors received from the pod library in `:on-error` to `*err*`.

A user will then use `pod.babashka.filewatcher/watch` like this:

``` clojure
$ clj
Clojure 1.10.1
user=> (require '[babashka.pods :as pods])
nil
user=> (pods/load-pod "pod-babashka-filewatcher")
nil
user=> (require '[pod.babashka.filewatcher :as fw])
nil
user=> (fw/watch "/tmp" (fn [result] (prn "result" result)))
nil
user=> (spit "/tmp/foobar123.txt" "foo")
nil
user=> "result" {:path "/private/tmp/foobar123.txt", :type "create"}
```

## Run tests

To run the tests for the pods library:

```
$ script/test
```

## License

Copyright © 2020 Michiel Borkent

Distributed under the EPL License. See LICENSE.
