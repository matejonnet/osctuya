OSC Tuya Bridge
===============

Motivation
----------
I want to get more out of the bulbs than just dimming and changing the colors of the individual bulb.
I want to make the bulbs work in sync, to follow a predefined choreography. 

To be able to control all the lights from the same tool,
I needed a professional lighting tool and such tools usually work with DMX and OSC protocols.

Tested with
-----------
  - Ledvance SMART+ WiFi RGB and White bulbs (the bulbs were unleashed first).
  - QLCPlus

Run your own
------------
Requirements:
- Java Openjdk 17
- Apache Maven 3.8.4

Compile:

    mvn clean install

Run:

    java -jar target/osctuya-1.0.0-jar-with-dependencies.jar ./src/test/resources/bulbs.yaml
