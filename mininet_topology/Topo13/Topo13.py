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
 
    net = Mininet( topo=None,
                   build=False,
                   ipBase='192.0.0.0/8',link=TCLink)
 
    info( '*** Adding controller\n' )
    c0 = net.addController('c0',controller=RemoteController,ip='192.168.123.212',port=6653)

    info( '*** Add switches & Routers \n')
# Add hosts and switches
    s1 = net.addSwitch('s1', cls=OVSKernelSwitch, failMode='standalone',protocols='OpenFlow13')
    Source = net.addHost('Source', cls=Host,ip='192.1.1.1/24',defaultRoute=None)
    User1 = net.addHost('User1', cls=Host,ip='192.1.1.2/24',defaultRoute=None)

        # Add links
    net.addLink(Source, s1,intfName1='Source-eth0',intfName2='s1-eth1', delay='30ms',jitter=5, max_queue_size=1000, use_htb=True)

    net.addLink(User1, s1,intfName1='User1-eth0',intfName2='s1-eth2', delay='30ms', jitter=5, max_queue_size=1000, use_htb=True)   

##    net.addLink(Source, s1,intfName1='Source-eth0',intfName2='s1-eth1',  loss=1,jitter=5, max_queue_size=1000, use_htb=True)
##
##    net.addLink(User1, s1,intfName1='User1-eth0',intfName2='s1-eth2',  loss=1,jitter=5, max_queue_size=1000, use_htb=True)   


    
    info( '*** Starting network\n')
    info( '*** Starting controllers\n')
    for controller in net.controllers:
        controller.start()
 
    info( '*** Starting switches\n')
       


 


    
    net.build()

    
    c0.start()
    net.get('s1').start([])
    s1.start([c0])
#bw:50Mbits/q@123:35Mbits q@456:15Mbits
    s1.cmdPrint('ovs-vsctl add-br s1')
    s1.cmdPrint('ovs-vsctl set port s1-eth1 qos=@newqos -- \--id=@newqos create qos type=linux-htb other-config:max-rate=50000000 queues=123=@q1,456=@q2 -- \--id=@q1 create queue other-config:min-rate=35000000 -- \--id=@q2 create queue other-config:min-rate=15000000')

    

    CLI(net)
    net.stop()
 
if __name__ == '__main__':
    setLogLevel( 'info' )
    myNetwork()





                
