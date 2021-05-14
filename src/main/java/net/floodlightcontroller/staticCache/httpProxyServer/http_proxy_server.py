#coding:utf-8
from socket import *
import time
import threading
import os
# Create socket and bind to HTTP 8080
tcpSerPort = 8080
tcpSerSock = socket(AF_INET, SOCK_STREAM)
 
# Prepare a server socket
tcpSerSock.bind(('', tcpSerPort))
tcpSerSock.listen(5)    #listen to maximum 5 requests
 
total_buff=[]
R = threading.Lock()
while True:
    # Begin to receive requests from the client
    print 'Ready to serve...'
    tcpCliSock, addr = tcpSerSock.accept()
    print 'Received a connection from: ', addr
    message = tcpCliSock.recv(4096)

    # Extract the host name from  message
    header_content = message.split('\r\n\r\n', 1)[0].split('\r\n')[1:]
    result = {}
    for line in header_content:
        k, v = line.split(': ')
        if k == 'Host':
            hostName = v.split(':')[0]

    # Extract the filename from request
    print message.split()[1]
    filename = message.split()[1].partition("/")[2]
    fileExist = "false"
    filetouse = "/" + filename
    try:
        # Check if the file exist from the cache
        f = open(filetouse[1:], "r")
        outputdata = f.readlines()
        fileExist = "true"
        print 'File Exists!'
 
        # File Exsits, send the file back to the client
        for i in range(0, len(outputdata)):
            tcpCliSock.send(outputdata[i])
        print 'Read from cache'
 
        # The file does not exist, process the exception
    except IOError:
        print 'File Exist: ', fileExist
        if fileExist == "false":
            # Create a tcp socket on proxy server
            print 'Creating socket on proxyserver'
            request_socket = socket(AF_INET, SOCK_STREAM)

            print "The file name is " + filename

            print 'Host Name: ', hostName
            try:
                # Connect to original destination server's 8080 port
                request_socket.connect((hostName, 8080))
                print 'Socket connected to port 8080 of the host'

                # Use makefile to cache the request file
                fileobj = request_socket.makefile('rw',0)
                # Append http get request
                try:
                    print "begin to write"
                    fileobj.write("GET /" + filename + " HTTP/1.1\r\nHost:" + hostName +":8080\r\nConnection: keep-alive\r\nAccept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8\r\nAccept-Encoding: gzip, deflate\r\nUpgrade-Insecure-Requests: 1\r\nUser-Agent: Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:88.0) Gecko/20100101 Firefox/88.0\r\nAccept-Language: en-US,en;q=0.5\r\n\r\n")
                except Exception,e:
                    print e
                    print "fail to write the request into fileobj"
                # Read the response into buffer
                request_socket.settimeout(3)

                try:
                    while True:
                        buff = request_socket.recv(20480)
                        if not buff: 
                            print "empty, break"

                            break
                        else:

                            tcpCliSock.send(buff)
                except Exception,e:
                    print e
                    print "loop recv failed"
                    if e.message == 'error104 connection reset by peer':

                        break;
                        
                    if e.message == 'timed out':
                        print "cache the source"
                        os.system("wget http://" + hostName+":8080/"+filename)
            except Exception,e:
                print e
               
                print "Illegal request"
        else:
            # HTTP response message for file not found
            # Do stuff here
            print 'File Not Found...Stupid Andy'
            a = 2
    # Close the client and the server sockets
    tcpCliSock.close()
# Fill in start.
tcpSerSock.close()
# Fill in end.
