
# Table of Contents

1.  [Ginkgo Floodlight OpenFlow Controller](#org5a81ced)
    1.  [What is this Project?](#org0168fc0)
    2.  [What is OpenFlow?](#orga3407b1)
    3.  [Getting Started](#org18c1e97)
        1.  [1. Recommendation way](#org355ff40)
    4.  [Functional Modules](#orgc26d3fb)
        1.  [Real-time video transmission module](#orge960938)
        2.  [Non-real-time video transmission module](#org1a672e4)
    5.  [Future works](#orgefddcd7)
    6.  [Authors](#orgf7dd218)


<a id="org5a81ced"></a>

# Ginkgo Floodlight OpenFlow Controller


<a id="org0168fc0"></a>

## What is this Project?

This is a research and development project, which is aims to implement an Application of Software Defined Network Technology in Campus Area Level Multimedia Service Guarantee, the controller is developed by team Ginkgo based on Floodlight.


<a id="orga3407b1"></a>

## What is OpenFlow?

OpenFlow is a open standard managed by Open Networking Foundation. It specifies a protocol by which a remote controller can modify the behavior of networking devices through a well-defined “forwarding instruction set”. Floodlight is designed to work with the growing number of switches, routers, virtual switches, and access points that support the OpenFlow standard.


<a id="org18c1e97"></a>

## Getting Started


<a id="org355ff40"></a>

### Recommendation way

Recommended to use maven to manage the project. Clone this project to local and open with a Java IDE and let your IDE to help you to finish the rest of dirty works.There are serverl steps:

1. Run the maven install if it is your first time open this project with you IDE. This is for install dependency in you local maven repository.
2. After the maven install SUCESS, you can start and run the Main.java 

Or more straightforwardly, go the file fold [see here](https://github.com/medic0803/Ginkgo-RnD-Project-Floodlight/tree/dev/GinkgoController) in your local and run our SDN controller using command: 

    java -jar floodlight_ginkgo.jar -cf floodlightdefault.properties

Note: in order to run the java, you need jre or jdk in your system environment; -cf means config file using the floodlightdefault.properties in the same fold; You may generate this .jar file using maven package command.


<a id="orgc26d3fb"></a>

## Functional Modules


<a id="orge960938"></a>

### Real-time video transmission module

1.  Module Dependencies

    1.  MulticastManager
    2.  ILinkDiscoveryService
    3.  IFloodlightProviderService
    4.  IRestApiService
    5.  QosResourceMonitor

2.  Service Implementation

    1.  IFetchMulticastGroupService


<a id="org1a672e4"></a>

### Non-real-time video transmission module

1.  Module & Service Dependencies

    1.  StaticCacheManager
    2.  IRoutingService
    3.  IFloodlightProviderService
    4.  IRestApiService
    5.  QosResourceMonitor
    6.  IDeviceService
    7.  IOFSwitchService

2.  Service Implementation

    1.  IStaticCacheService


<a id="orgefddcd7"></a>

## Future works

We believe that the openflow especially the Floodlight controller may not be so popular in the future, but if you want to contribute to this project or have any difficulties while developing similar floodlight project, we are welcome to the questions via issues, E-mail, just because it is also a hard time for us with poor information support during the development.

We also list some functional features we do not complete:

1.  QoS routing monitor module
2.  Using algorithm to upgrade the precision and refine the Floodlight's cost of system resources.
3.  Figure out the way of measure the pack loss in the virtual enironment mininet.
4.  Change the Source-based tree to Shared tree.
5.  Web page controller controls the user groups
6.  Security policy module
7.  Implement multiple network segements and connect to the internet in the experimental topology
8.  Implement the controller to real campus network environment


<a id="orgf7dd218"></a>

## Authors

Thanks for all the team members' contribution in last one year, sincerely hope that this could be a precious experience for you!
<https://github.com/medic0803/Ginkgo-RnD-Project-Floodlight/graphs/contributors>
