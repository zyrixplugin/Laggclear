
How to setup your config!

Where is the config? plugins\ClearLag\config.yml

 
Configuring Entity Filter/Remover Lists

Entity lists in clearlagg are highly configurable. You're able to filter based on many attributes such as name, livetime, and whether the entity is actively mounted. These filters are available for all entity lists!

These attributes can be reversed by putting a ! in front of it. So for example, !isMounted would be "Is NOT mounted", while isMounted would be "IS mounted".
Attribute Filters:

    hasName - This will filter any entity that has a custom name
    liveTime=100 - This will filter any entity that has lived for 100 ticks
    isMounted - This will filter any entity that is currently mounted
    name="Bob" - This will filter any entity with the name Bob
    id=1 - This will filter any ITEM entity with the ID of 1 (stone)
    onGround - This filter will filter any entity on the ground
    hasMeta="Yup" - This filter will filter any entity containing  the meta Yup

Examples:

The following config section will filter any Minecart that IS currently mounted from being removed by /lagg area

area-filter:
    - Minecart isMounted

This will remove any Pig named Testing, any Zombie WITH a name, and any Minecart that IS NOT mounted

chunk-entity-limiter:
  enabled: true
  limit: 10
  entities:
    - Pig name="Testing"
    - Zombie hasName
    - Minecart !isMounted


You're able to define more then one entity of the same type, like so:

entities:
   - Item id=1
   - Item id=2
   - Pig name="bob7l"
   - Pig name="Joe"



Configuring Custom Entity Removal

The "custom-trigger-removal" section of the configuration allows you to get more technical, and custom with the way you want to handle certain conditions of your server such as TPS levels, and entity counts. Being that this is the initial release, there isn't too many options yet. I plan to add onto this in time.

How to specify a trigger:
A trigger is what executes your jobs. You may specify UNLIMITED triggers, BUT all must have a UNIQUE name due to how YAML parses the config file. By default, I specify each with trigger1, trigger2, ect. How you name them is entirely up to you as long as they're valid YAML keys (No spaces)

"run-interval" specifies, in seconds, how often that trigger's conditions should be checked. 

To specify a trigger (REQUIRED), you must put the trigger type under "trigger-type:". Each trigger requires different variables to operate correctly so make sure you specify them under the trigger type.

Trigger types:

tps-trigger: This trigger will execute your jobs once your TPS hits below your specified "tps-trigger" target, and will disable upon reaching your "tps-recover" target. 

entity-limit-trigger: This trigger executes all your jobs once the specified entity limits are reached. For this to function, you must specify a "limit", a list of entities to be counted "entity-limits", and you may optionally specify worlds to be filtered (Not checked) using "world-filter:". Examples of all of this are shown below. 

Jobs: 
Jobs are what execute after a trigger's conditions are met. Some jobs will prevent the trigger's from releasing (Warning's). You may specify multiple jobs as long as they're different types. You cannot specify multiple jobs of the same type.

ALL jobs may be given warning countdowns - or just delay's if you decide not to specify any warnings. All you need to do is add "execute-job-time", and put the time in seconds it should take until the job is executed. Then, if you'd like warning messages, just like auto-removal - you need to add "warnings:", and put your warnings below. An example of this is started below.

Job types:

entity-clearer: This cleaner job removes the entities you specify under "remove-entities". In order to filter out worlds (NOT remove from), you need to specify their names under "world-filter:".

command-executor: This job executes all the commands under "commands:" you want to be executed after the trigger's conditions are met. After the trigger recovers, all the commands under "recover-commands" are then executed. 

Examples:
(Sorry for format, dev.bukkit.org is very buggy/broken)

custom-trigger-removal: <---- The root. Don't change this
  enabled: true  <---- Should this whole thing even be enabled? By default, disabled
  triggers: <---- Specifies your list of triggers. Don't change this name
    trigger1: <--- Our first trigger. We want to kill mobs when the TPS hits 19.4!
      trigger-type: tps-trigger  <---- Specifying our trigger-type
      run-interval: 5   <----- We want this to run every 5 seconds
      tps-trigger: 19.4  <----- Run the job(s) at 19.4 TPS!
      tps-recover: 19.97  <----- Disable the jobs at 19.97 TPS
      jobs:  <----- Root name for job listings. Don't rename
        command-executor:  <----- We're using job type "command-executor"
          commands:   <----- Specifying what commands we wanna run!
            - 'lagg killmobs'
          recover-commands: []  <----- We don't want to run recovery commands
        entity-clearer: <----- We're using job type "entity-clearer" as a second job
          execute-job-time: 21 <----- Let's give players a 21 second warning before removing items
          warnings:  <----- Specify our warnings. time = how many seconds it's been
            - 'time:5 msg:&4[ClearLag] &cEntities/drops will be purged in &7+remaining &cseconds!'
            - 'time:15 msg:&4[ClearLag] &cEntities/drops will be purged in &720 &cseconds!'
            - 'time:20 msg:&4[ClearLag] &cEntities/drops will be purged in &710 &cseconds!'
          world-filter:
            - world <----- This world will be ignored during removal
          remove-entities: <----- What entities to remove
            - item
            - zombie !hasName  <----- Remove zombies WITHOUT a name
    myOtherTriggerYup: <----- Neat name? As I've said, can be any valid YAML key name
      trigger-type: entity-limit-trigger <----- Setting trigger type
      run-interval: 5  <----- Run every 5 seconds
      limit: 10  <----- Limit our "entity-limits" types to 10 total
      world-filter: [] <----- We don't wanna filter any worlds
      entity-limits: <----- Which entities should be counted towards limit?
        - zombie
        - skeleton
        - enderman
      jobs: <----- Specifying our jobs
        entity-clearer: <----- Using job type "entity-clearer"
          world-filter: [] <----- We don't wanna filter any worlds
          remove-entities:  <----- Which entities should we remove?
            - zombie
            - armorstand
            - enderman
    trigger3: <----- This trigger we're going to remove arrows in the ground
      trigger-type: entity-limit-trigger
      run-interval: 5
      limit: 5 <----- Limit is 5 arrows in the ground before entity-clearer is executed
      world-filter:
        - world
      entity-limits:
        - arrow inGround <----- Only arrows in the ground
      jobs:
        entity-clearer:
          world-filter: []
          remove-entities:
            - arrow inGround

