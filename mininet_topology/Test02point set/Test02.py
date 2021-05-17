#!/usr/bin/python

from mininet.net import Mininet
from mininet.node import CPULimitedHost
from mininet.node import Host, Node
from mininet.cli import CLI
from mininet.log import setLogLevel
from mininet.log import info
from mininet.link import TCLink
from subprocess import call
from mininet.topo import Topo
from mininet.util import dumpNodeConnections
from mininet.node import Controller, RemoteController, OVSController


from mininet.node import Node, Switch
from mininet.link import Link, Intf
from mininet.log import setLogLevel, info





class MyTopo( Topo ):
    "Simple topology example."
    
    def __init__( self, **opts ):
        "Create custom topo."
        Topo.__init__(self, **opts)
        info( '*** Add hosts\n')
              


        User1 = self.addHost('User1', ip='10.1.1.1/24', cpu=.2, defaultRoute=None)
        User2 = self.addHost('User2', ip='10.1.1.2/24', cpu=.2, defaultRoute=None)
        User3 = self.addHost('User3', ip='10.1.2.1/24', cpu=.2, defaultRoute=None)
        User4 = self.addHost('User4', ip='10.1.2.2/24', cpu=.2, defaultRoute=None)
        Router1= self.addNode('r1', cls=LinuxRouter,ip='10.1.1.0/24')
        Router2= self.addNode('r2', cls=LinuxRouter,ip='10.1.2.0/24')



        

        info( '*** Add switches\n')
        
        s1 = self.addSwitch('s1')

        
        

        info( '*** Adding controller\n' )
        

        info( '*** Starting switches\n')


        info( '*** Add links\n')


        self.addLink(User1, r1,intfName1='User1-eth1',intfName2='r1-eth1',params2={'ip':'10.1.1.255/24'} ,bw=50, delay='10ms', loss=0,jitter=0)
        self.addLink(User2, r1, intfName1='User2-eth1', intfName2='r1-eth2', params2={'ip': '10.1.1.254/24'}, bw=50,delay='10ms', loss=0, jitter=0)
        self.addLink(User3, r2,intfName1='User3-eth1',intfName2='r2-eth1',params2={'ip':'10.1.2.255/24'} ,bw=50, delay='10ms', loss=0,jitter=0)
        self.addLink(User4, r2, intfName1='User4-eth1', intfName2='r2-eth2', params2={'ip': '10.1.2.254/24'}, bw=50,delay='10ms', loss=0, jitter=0)
        self.addLink(s1, r1,intfName1='s1-eth1',intfName2='r1-eth3',params2={'ip':'10.1.1.253/24'} ,bw=50, delay='10ms', loss=0,jitter=0)
        self.addLink(s1, r2, intfName1='s1-eth2', intfName2='r2-eth3', params2={'ip': '10.1.1.253/24'}, bw=50,delay='10ms', loss=0, jitter=0)

               


        

def runMyTopo():
    topo = MyTopo()
    net = Mininet(topo=topo,host=CPULimitedHost,switch=OVSSwitch, link=TCLink )
    
             


        


    info( '*** Starting network\n')
    net.start()
    dumpNodeConnections(net.hosts)
    CLI( net )
    net.pingAll()

    info( '*** Starting controllers\n')
    for controller in net.controllers:
        controller.start()


    info( '*** Post configure switches and hosts\n')
    info( '*** Starting switches\n')


    net.get('s1').start([])


    info( '*** Post configure switches and hosts\n')



if __name__ == '__main__':
    # This runs if this file is executed directly
    setLogLevel( 'info' )
    Mininet.init()
    runMyTopo()
              

        
topos = { 'mytopo': ( lambda: MyTopo() ) }



