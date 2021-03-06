JTRANSC
-------

![JTransc](extra/logo-256.png)

[![Maven Version](https://img.shields.io/github/tag/jtransc/jtransc.svg?style=flat&label=maven)](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22jtransc-maven-plugin%22)
[![Build Status](https://secure.travis-ci.org/jtransc/jtransc.svg)](http://travis-ci.org/#!/jtransc/jtransc) [![Build status](https://ci.appveyor.com/api/projects/status/qnd0g966t1b54q4a?svg=true)](https://ci.appveyor.com/project/soywiz/jtransc)
[![irc](https://img.shields.io/badge/irc:%20chat.freenode.net-%23jtransc-green.svg)](http://webchat.freenode.net/?channels=#jtransc)
[![Join the JTransc Community on Slack](http://jtransc-slack.herokuapp.com/badge.svg)](http://jtransc-slack.herokuapp.com/)

# Documentation

You can find documentation at the [wiki](https://github.com/jtransc/jtransc/wiki).

# What is this?

Jtransc (Java Trans Compiler) is an AOT (ahead of time compiler) that compiles .class and .jar files
into a target executable file bundling all the required dependencies in a single file, without requiring
a jitter or an external runtime.
At the beggining it generated as3 and javascript, but right now there is a single target: haxe.
This allows targeting js, as3, c++, c#, java, php and python. This in turn allows running the program on different platforms such as desktop, browsers and mobile.

The aim of this project is to bring the high productivity of Kotlin (and other JVM languages)
to the highly portable haxe platform and other direct targets in the future.
It already supports some APIs and plain Java reflection API out of the box.

The initial focus is on jvm6, kotlin, haxe and games, but it will get better in the future supporting newer jvm versions,
and other use cases like frontend and backend applications.

There is a module called jtransc-rt-core that could be included in any project (whether using jtransc or not).
It provides the com.jtransc package, specific annotations, fast memory access and asynchronous APIs,
that will use jtransc features when compiling using jtransc.

These is also a couple of projects for multimedia applications using jtransc:
* [jtransc-media](https://github.com/jtransc/jtransc-media) - Which provides a very simple and portable high-level API for multimedia
* [gdx-backend-jtransc](https://github.com/jtransc/gdx-backend-jtransc) - Which provides a gdx-compatible backend so any gdx project will be able to work (still some rough edges)

# How to use it?

You can find examples here [jtransc/jtransc-examples](https://github.com/jtransc/jtransc-examples).

## Plain:
```
# jtransc script can be found under the jtransc-main-run folder
javac com/test/Main.java -d target/classes
jtransc dependency.jar target/classes -main com.test.Main -out program.js
node program.js
```

## Maven:

This is the preferred way of using jtransc.

You can search for artifacts for it in maven central with [com.jtransc groupId](http://search.maven.org/#search%7Cga%7C1%7Cg%3Acom.jtransc).

`pom.xml` file should include.
Until documentation is finished, you can find mojo's available options here:
[https://github.com/jtransc/jtransc/blob/master/jtransc-maven-plugin/src/com/jtransc/maven/mojo.kt#L38](https://github.com/jtransc/jtransc/blob/master/jtransc-maven-plugin/src/com/jtransc/maven/mojo.kt#L38)

```
<plugins>
    <plugin>
        <groupId>com.jtransc</groupId>
        <artifactId>jtransc-maven-plugin</artifactId>
        <version>0.2.3</version>
        <configuration>
			<mainClass>example.Test</mainClass>
			<targets>
				<param>lime:swf</param>
				<param>lime:js</param>
				<param>lime:neko</param>
				<param>lime:android</param>
			</targets>
			<release>true</release>
			<minimizeNames>false</minimizeNames>
        </configuration>
        <executions><execution><goals><goal>jtransc</goal></goals></execution></executions>
    </plugin>
</plugins>

```

```
mvn package # it should generate program.swf
```

## intelliJ:

There is a plugin in the works that will allow to run and debug within intelliJ. Though it is not ready yet.
You can find it in [jtransc-intellij-plugin](https://github.com/jtransc/jtransc/tree/master/jtransc-intellij-plugin) folder.

# How does it work internally?

* It locates all the required dependencies (specifying dependencies, using maven or intelliJ)
* It includes jtransc-rt-core and jtransc-rt which is a java-6-like rt with some of their methods marked as natives
* Other dependencies than the RT are included without modifications
* It uses ASM to generate a class-method-statement-expression AST
    * That AST is easily serializable
    * That AST allows feature stripping
    * Your target language don't support gotos? It will generate an AST without gotos. Just plain if/while/switch...
* It generates your target source code, replacing some classes like String, ArrayList and so on, to make them fast in your target language.
* It joins or compiles that code into your final runnable program

Eventually that intermediate AST will be able to be generated or consumed.
So others could generate that without JVM and others could generate other targets from that AST directly without all the complexities of stack-based IRs.

## Sponsored by:

![Akamon Entertainment](extra/akamon.png)
