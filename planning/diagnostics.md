## Operation Definition (Functions with InlineStream as receiver, that call )
Errors:
- InlineStream operation function is not `inline`
- Calls `streamOperation` more than once
- Calls `streamOperation` and doesn't return it
- Calls both `streamOperation` and `terminalOperation`
- References InlineStream inside of `streamOperation`
- Calls `streamOperation` or `terminalOperation` outside the top level of the function

Questioning:
- InlineStream operation function is not top-level?
    - I mean, I can't see a problem with it... but for now, it's easier for all of them to be top-level


## _Any_ function definition
- Error: Uses an InlineStream as a parameter of any kind (because it's not a real object)
    - suggests using a regular Sequence
- Error: Uses an InlineStream as a receiver parameter without being annotated with @InlineStreamOperation


## Usage within a function
Errors:
- Lets an InlineStream "object" escape a function
    - Returns it
    - Passes it to another function (including as receiver to a non-operation function)
    - Assigns it to a field in an object
- Attempts to assign an InlineStream object to a variable (again, not a real object)
    - Note: we need a way to let a user choose what's done on the loop fluidly, maybe an API function like "if else" that lets users create a condition and conditionally execute one downstream path or another downstream path?
- Uses it as a context parameter
- Doesn't have a terminal operation
