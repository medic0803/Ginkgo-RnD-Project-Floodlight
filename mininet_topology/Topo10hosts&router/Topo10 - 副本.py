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
from mininet.node import OVSKernelSwitch, UserSwitch
 
def myNetwork():
 
    net = Mininet( topo=None,
                   build=False,
                   ipBase='10.0.0.0/8',controller=RemoteController,host=CPULimitedHost,link=TCLink,switch=UserSwitch)
 
    info( '*** Adding controller\n' )
    net.addController('c0',controller=RemoteController,ip='192.168.56.1',port=6653)
    
    info( '*** Add routers\n')
    r1 = net.addHost('r1', cls=Node, ip='0.0.0.0')
    
    info( '*** Add switches\n')
##    s1 =net.addSwitch('s1')
##    s2 =net.addSwitch('s2')
    
##    switch = net.switches[ 0 ]
 
    info( '*** Add hosts\n')
    h1 = net.addHost('h1', cls=Host, ip='192.168.11.1/24', defaultRoute=None)
    h2 = net.addHost('h2', cls=Host, ip='192.168.12.1/24', defaultRoute=None)

   
 
    info( '*** Add links\n')
##    net.addLink(h1, s1, cls=TCLink )
##    net.addLink(h2, s2, cls=TCLink )
##    net.addLink(r1, s1, cls=TCLink )
##    net.addLink(r1, s2, cls=TCLink )
##
    net.addLink(r1, h1, cls=TCLink )
    net.addLink(h2, r1, cls=TCLink )
    
    info( '*** Starting network\n')
    net.build()
    info( '*** Starting controllers\n')
    for controller in net.controllers:
        controller.start()
 
    info( '*** Starting switches\n')
    
 
    info( '*** Post configure switches and hosts\n')
    r1.cmd('ifconfig r1-eth0 192.168.11.2 netmask 255.255.255.0')
    r1.cmd('ifconfig r1-eth1 192.168.12.2 netmask 255.255.255.0')
##    r1.cmd('ifconfig r1-eth3 10.0.2.225 netmask 255.255.255.0')


    
    
 
    h1.cmd('route add default gw 192.168.11.2')
    h2.cmd('route add default gw 192.168.12.2')
    
##    r1.cmd('route add default gw 192.168.56.1')


 
    r1.cmd('sysctl net.ipv4.ip_forward=1')
  
    CLI(net)
    net.stop()
 
if __name__ == '__main__':
    setLogLevel( 'info' )
    myNetwork()
