#coding:utf-8
from socket import *
 
# Create socket and bind to HTTP 8080
tcpSerPort = 8080
tcpSerSock = socket(AF_INET, SOCK_STREAM)
 
# Prepare a server socket
tcpSerSock.bind(('', tcpSerPort))
tcpSerSock.listen(5)    #listen to maximum 5 requests
 
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
#            hostName = filename.replace("www.", "", 1)

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
                    fileobj.write("GET /" + filename + " HTTP/1.1\r\nHost: 10.0.0.1:8080\r\nConnection: Keep-Alive\r\n Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8\r\nAccept-Encoding: identity\r\nUpgrade-Insecure-Requests: 1\r\nUser-Agent: Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/45.0.2454.101 Safari/537.36\r\nAccept-Language: zh-CN,zh;q=0.8\r\n\r\n")
                except Exception,e:
                    print e
                    print "fail to write the request into fileobj"
                # Read the response into buffer
                request_socket.settimeout(5)
                total_buff=[]
                try:
                    while True:
                        print "Begin to receive data"
                        buff = request_socket.recv(20480)
                        if not buff: 
                            print "empty, break"
                            break
                        else:
                            print "receive buff"
                        total_buff.append(buff)
                except Exception,e:
                    print e
                    print "loop recv failed"
                try:
#                    buff = fileobj.readlines()
                    fileobj.close()
#                    print buff
                except Exception,e:
                    print e
                    print "readlines() failed"

                # Create a new file in the cache for the requested file.
                # Also send the response in the buffer to client socket
                # and the corresponding file in the cache
                try:
                    tmpFile = open("./" + filename,"wb")
                except:
                    print "create temp file failed"

                for line in total_buff:
                    tmpFile.write(line)
                    tcpCliSock.send(line)
#                tcpCliSock.send(buffer)
#                for i in range(0, len(buff)):
#                    tmpFile.write(buff[i])
#                    tcpCliSock.send(buff[i])
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
