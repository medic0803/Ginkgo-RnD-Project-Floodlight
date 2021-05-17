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
 
def myNetwork():
 
    net = Mininet( controller=RemoteController, switch=OVSKernelSwitch)
 
    info( '*** Adding controller\n' )
    c0 = net.addController('c0',controller=RemoteController,ip='192.168.123.212',port=6653)

    info( '*** Add switches & Routers \n')
# Add hosts and switches
    Router1 = net.addHost('Router1',ip='192.168.2.1')
    Router2 = net.addHost('Router2',ip='192.168.3.1')
    User1 = net.addHost('User1',ip='192.168.2.2')
    User2 = net.addHost('User2',ip='192.168.3.2')


        # Add links
 
    net.addLink(User1,Router1,intfName1='User1-eth0',intfName2='Router1-eth0')
                        
    net.addLink(User2,Router2,intfName1='User2-eth0',intfName2='Router2-eth0')

    net.addLink(Router1, Router2,intfName1='Router1-eth1',intfName2='Router2-eth1')


    
    info( '*** Starting network\n')
    info( '*** Starting controllers\n')
    for controller in net.controllers:
        controller.start()
 
    info( '*** Starting switches\n')
        ##    open the eth



    
    net.build()


    c0.start()



##    Router1.cmd('ifconfig Router1-eth1 192.168.1.2 netmask 255.255.255.0')
##    Router1.cmd('sysctl net.ipv4.ip_forward=1')
##    Router2.cmd('ifconfig Router1-eth1 192.168.1.3 netmask 255.255.255.0')
##    Router2.cmd('sysctl net.ipv4.ip_forward=1')
    

    
    Router1.cmdPrint('ifconfig Router1-eth1 192.168.1.2 netmask 255.255.255.0')
    Router1.cmdPrint('sysctl net.ipv4.ip_forward=1')
    Router2.cmdPrint('ifconfig Router2-eth1 192.168.1.3 netmask 255.255.255.0')
    Router2.cmdPrint('sysctl net.ipv4.ip_forward=1')

    User1.cmdPrint('route add default gw 192.168.2.1')
    User2.cmdPrint('route add default gw 192.168.3.1')
    Router1.cmdPrint('route add default gw 192.168.1.3')

    

    
    net.pingAll()
    CLI(net)
    net.stop()
 
if __name__ == '__main__':
    setLogLevel( 'info' )
    myNetwork()





