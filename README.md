TS3 Manager
===========

A modular multi-server ts3 bot running over the ts3query.
This project comes with 2 built in mods.
- Statistics Bot logging the current user amount everytime one joins/leaves into MariaDB / 
- Rocket bot, it is meant more as a joke and will throw a user through every (non) taken channel in a blink of a second and then kick him.

### Requirements
- JRE 7 or higher
- TS3 with query access
- MariaDB at best if you want to use the statistics module

For compiled binaries see section "release"

### Notes
This is only a backend, a visualization like the following for your statistics is up to you.

<div align="center">
<a><img src="stats.png" /><a/> 
</div>
  
You should be the admin of your ts3 server with an access to the query whitelist to avoid problems.

### License
APGL http://www.gnu.org/licenses/agpl.html  
Except for .jar's, and de.stefan1200.*
