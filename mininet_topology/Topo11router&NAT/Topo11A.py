#!/usr/bin/python

from mininet.net import Mininet
from mininet.node import Controller, RemoteController, OVSController
from mininet.node import CPULimitedHost, Host, Node
from mininet.node import OVSKernelSwitch, UserSwitch
from mininet.node import IVSSwitch
from mininet.cli import CLI
from mininet.log import setLogLevel, info
from mininet.link import TCLink, Intf
from subprocess import call



######################################################################
##def checkIntf( intf ):
##    "Make sure intf exists and is not configured."
##    if ( ' %s:' % intf ) not in quietRun( 'ip link show' ):
##        error( 'Error:', intf, 'does not exist!\n' )
##        exit( 1 )
##    ips = re.findall( r'\d+\.\d+\.\d+\.\d+', quietRun( 'ifconfig ' + intf ) )
##    if ips:
##        error( 'Error:', intf, 'has an IP address,'
##               'and is probably in use!\n' )
##        exit( 1 )
########################################################################
        
def myNetwork():
 
    net = Mininet( topo=None,
                   build=False,
                   ipBase='192.0.0.0/8',controller=RemoteController,host=CPULimitedHost,link=TCLink, switch=OVSKernelSwitch)
 
    info( '*** Adding controller\n' )
    c0 = net.addController('c0',controller=RemoteController,ip='192.168.123.212',port=6653)

    info( '*** Add switches & Routers \n')
# Add hosts and switches
    s1 = net.addSwitch('s1')
    s2 = net.addSwitch('s2')
    h1 = net.addHost('h1', ip='10.0.1.2/24',cls=Host,defaultRoute=None)
    h2 = net.addHost('h2', ip='10.0.2.2/24',cls=Host,defaultRoute=None)
    r1 = net.addHost('r1', cls=Host,defaultRoute=None)
    s3 = net.addSwitch('s3')

        # Add links
    net.addLink(h1, s1)
    net.addLink(h2, r1,intfName2='r1-eth2')
    net.addLink(s1, s2)
    net.addLink(r1, s2,intfName1='r1-eth1')
    net.addLink(s3, r1,intfName2='r1-eth3')

    
    info( '*** Starting network\n')
    info( '*** Starting controllers\n')
    for controller in net.controllers:
        controller.start()
 
    info( '*** Starting switches\n')
        ##    open the eth
########################################################################
    intfName = sys.argv[ 1 ] if len( sys.argv ) > 1 else 'enp0s9'
##    info( '*** Connecting to hw intf: %s' % intfName )
## 
##    info( '*** Checking', intfName, '\n' )
##    checkIntf( intfName )
## 
##    info( '*** Creating network\n' )
##    net = Mininet( topo=MyTopo(),controller=None) 
##    switch = net.switches[ 0 ]
##    info( '*** Adding hardware interface', intfName, 'to switch', switch.name, '\n' )
    _intf = Intf( intfName, s3 ) 

     
########################################################################
    
    r1.cmd('ifconfig r1-eth1 10.0.1.1/24')
    r1.cmd('ifconfig r1-eth2 10.0.2.1/24')
    r1.cmd('ifconfig r1-eth3 10.0.4.25/24')
##    s3.cmd('s3 ovs-vsctl add-port s3 enp0s9')
    h1.cmd('route add default gw 10.0.1.1')
    h2.cmd('route add default gw 10.0.2.1')
    r1.cmd('route add default gw 10.0.4.2')
    r1.cmd('sysctl net.ipv4.ip_forward=1')
                
    net.build()
    c0.start()
    s1.start([c0])
    s2.start([c0])
    s3.start([c0])

    

    CLI(net)
    net.stop()
 
if __name__ == '__main__':
    setLogLevel( 'info' )
    myNetwork()
    

