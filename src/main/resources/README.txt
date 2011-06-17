This library contains MultiServiceTracker class used to manage hierarchical 
dependencies between OSGi services.
Each object managed by MultiServiceTracker is activated only when all required
service dependencies are resolved and injected in this object. An activated 
object can itself automatically expose a set of services.
This library can be used as a lightweight (an simplified) alternative to 
inversion-of-control (IOC) frameworks like Guice or Spring IOC.  