Alright, now how do we actually implement the damn thing


Stipulations:
- Everything pretends it's an actual object, same as with Sequence and Stream
- Said object cannot leave the function it's in if it's not inside an inline function, at least not without being "boxed" into a deferred sequence
  - wait, if we allow inline functions... we could actually make a full framework for this with a functional interface
- Chaining functions actually nests them
- All of this is inlined
- Each "step" is given a "get next element function"


Problems:
- wait a second, how does "dropping" work if the functors can't hold state between iterations? 
  - that would have to be in the terminal operation, but then people will want to chain stuff after that too
  - so do we pass each thing a "get next element" function instead of the element itsel- OH MY GOD RIGHT RIGHT RIGHT LAMBDA CALCULUS STRIKES AGAIN
  - if we give it a "get next element" function, it can freely cycle through... uhhh WAIT YEAH OF COURSE yeah we give the inner step a modified getNextElement function that counts how many it's done and drops those? or uhhhh

