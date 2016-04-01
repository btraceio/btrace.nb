# BTrace NetBeans Integration

This project provides a tight integration of [BTrace](http://github.com/btraceio/btrace) in the [NetBeans IDE](http://www.netbeans.org).

## BTrace API (btrace.api)
The API module provides all the necessary abstractions for other NetBeans modules to work with BTrace seamlessly. Basically it wraps the BTrace low level classes to a set of providers and services which are consumable by the rest of the IDE ecosystem.

## IDE plugin (ide.support/btrace.netbeans)
This modules defines new project type, new file type and bindings to editor to work with BTrace comfortably. A _BTrace project_ will come with pre-configured BTrace library.

Any @BTrace annotated class opened in the editor will have additional functionality enabled - eg. attaching to any discoverable JVM process and submitting the script directly.
