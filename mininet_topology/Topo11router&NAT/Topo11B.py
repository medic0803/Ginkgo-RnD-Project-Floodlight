
#!/usr/bin/python
import re
import sys
from mininet.net import Mininet
from mininet.node import Controller, RemoteController, OVSController
from mininet.node import CPULimitedHost, Host, Node
from mininet.node import OVSKernelSwitch, UserSwitch
from mininet.node import IVSSwitch
from mininet.cli import CLI
from mininet.log import setLogLevel, info
from mininet.link import TCLink, Intf
from subprocess import call
from mininet.topolib import TreeTopo
from mininet.topo import Topo
from mininet.util import quietRun
 
class MyTopo( Topo ):
#    "this topo is used for Scheme_1"
    
    def __init__( self ):
        "Create custom topo."
 
        # Initialize topology
        Topo.__init__( self )

       
 
        # Add hosts 
        h1 = self.addHost('h1', ip='10.0.1.2/24',cls=Host,defaultRoute='via 10.0.1.1')
        h2 = self.addHost('h2', ip='10.0.2.2/24',cls=Host,defaultRoute='via 10.0.2.1')
        r1 = self.addHost('r1', cls=Host,defaultRoute='via 10.0.4.2')
            
        # Add switches
        s1 = self.addSwitch('s1')
        s2 = self.addSwitch('s2')
        s3 = self.addSwitch('s3')
        
        # Add links
        self.addLink(h1, s1)
        self.addLink(h2, r1,intfName2='r1-eth2' )
        self.addLink(s1, s2)
        self.addLink(r1, s2,intfName1='r1-eth1' )
        self.addLink(s3, r1,intfName2='r1-eth3' )
        

def checkIntf( intf ):
    "Make sure intf exists and is not configured."
    if ( ' %s:' % intf ) not in quietRun( 'ip link show' ):
        error( 'Error:', intf, 'does not exist!\n' )
        exit( 1 )
    ips = re.findall( r'\d+\.\d+\.\d+\.\d+', quietRun( 'ifconfig ' + intf ) )
    if ips:
        error( 'Error:', intf, 'has an IP address,'
               'and is probably in use!\n' )
        exit( 1 )
 
if __name__ == '__main__':
    setLogLevel( 'info' )

 
    # try to get hw intf from the command line; by default, use eth1
##    intfName = sys.argv[ 1 ] if len( sys.argv ) > 1 else 'enp0s9'
    intfName = 'enp0s9'
    info( '*** Connecting to hw intf: %s' % intfName )
 
    info( '*** Checking', intfName, '\n' )
    checkIntf( intfName )
 
    info( '*** Creating network\n' )
    net = Mininet( topo=MyTopo(),ipBase='10.0.0.0/24',controller=None,host=CPULimitedHost,link=TCLink, switch=OVSKernelSwitch) 
    switch = net.switches[ 2 ]
    info( '*** Adding hardware interface', intfName, 'to switch', switch.name, '\n' )
    _intf = Intf( intfName, node=switch ) 

    info( '*** Note: you may need to reconfigure the interfaces for '
          'the Mininet hosts:\n', net.hosts, '\n' )
    c0 = net.addController('c0',controller=RemoteController,ip='192.168.123.212',port=6653)
##    c0 = RemoteController( 'c0', ip='192.168.123.212', port=6653 )
##    net.addController(c0)
    
##    s3.cmd('s3 ovs-vsctl add-br s3')    
##    s3.cmd('s3 ovs-vsctl add-port s3 enp0s9')
##    r1.cmd('ifconfig r1-eth1 10.0.1.1/24')
##    r1.cmd('ifconfig r1-eth1 10.0.2.1/24')
##    r1.cmd('ifconfig r1-eth1 10.0.4.25/24')    
##    h1.cmd('route add default gw 10.0.1.1')
##    h2.cmd('route add default gw 10.0.2.1')
##    r1.cmd('route add default gw 10.0.4.2')
##    r1.cmd('sysctl net.ipv4.ip_forward=1')


    
    net.start()

##    _intf = Intf( intfName, node=s3 )

    CLI( net )
    net.stop()
