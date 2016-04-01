JTS3ServerQuery library version 2.0.3
Author: Stefan Martens

E-Mail:
info@stefan1200.de

Homepage:
http://www.stefan1200.de


-= Copyright =-
This library is free for use, but please notify me if you found a bug or if you have some suggestion.
The author of this library is not responsible for any damage or data loss!
It is not allowed to sell this library for money, it has to be free to get!

Teamspeak 3 is developed by TeamSpeak Systems GmbH, Sales & Licensing by Triton CI & Associates, Inc.
More information about Teamspeak 3: http://www.teamspeak.com


-= Informations =-
This library allows you the easy use of the Teamspeak 3 server telnet query interface.
It should support all Teamspeak 3 servers, but some list arguments in the library documentation
may needs a newer Teamspeak 3 server version.

This library supports the events notify on telnet interface, which allows Teamspeak Clients
to communicate with query clients at the telnet interface by chatting and much more.

All methods of this library throw a IllegalStateException, IllegalArgumentException or TS3ServerQueryException.
IllegalStateException -> Not connected to TS3 server or an invalid response from server received.
IllegalArgumentException -> An argument don't match the requirements.
TS3ServerQueryException -> The TS3 server returned an error code/message.

The methods parseLine() and parseRawData() also throw a NullPointerException, if the given argument is null.

Notice:
You should add the IP of your program to the Teamspeak 3 server query_ip_whitelist.txt,
or the anti spam feature of the Teamspeak 3 server may ban your program for some minutes.


-= Own projects using this library =-
JTS3ServerMod, a powerful Teamspeak 3 server bot written in Java language, use this library.
This bot runs on all Java 5 enabled operating systems, known to be runable at: Windows, Linux and Mac OS X.
You can find more information about this at:
http://www.stefan1200.de/forum/index.php?topic=2.0 (English)
http://www.stefan1200.de/forum/index.php?topic=3.0 (German)
http://forum.teamspeak.com/showthread.php?t=51286 (English)


-= Content =-
This package contains two Java packages:
de.stefan1200.jts3serverquery
The TS3 Server Query library. Include this in your own project.

de.stefan1200.jts3serverqueryexample
The example how to use the library. Not needed in your own project.

See also the Java documentation of this library in the doc subdirectory.