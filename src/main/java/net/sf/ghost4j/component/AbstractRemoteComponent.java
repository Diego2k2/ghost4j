/*
 * Ghost4J: a Java wrapper for Ghostscript API.
 *
 * Distributable under LGPL license.
 * See terms of license at http://www.gnu.org/licenses/lgpl.html.
 */
package net.sf.ghost4j.component;

import gnu.cajo.Cajo;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import net.sf.ghost4j.converter.Converter;
import net.sf.ghost4j.converter.ConverterException;
import net.sf.ghost4j.converter.RemoteConverter;
import net.sf.ghost4j.util.JavaFork;
import net.sf.ghost4j.util.NetworkUtil;

import org.apache.log4j.Logger;

/**
 * Abstract remote converter component.
 * Used as base class for remote components.
 * @author Gilles Grousset (gi.grousset@gmail.com)
 */
public abstract class AbstractRemoteComponent extends AbstractComponent {

	/**
     * Log4J logger used to log messages.
     */
    private Logger logger = Logger.getLogger(AbstractRemoteComponent.class.getName());
    
    /**
     * Maximum number of parallel processes allowed for the converter.
     */
    protected int maxProcessCount = 0;
    /**
     * Number of parallel processes running.
     */
    protected int processCount = 0;
    
    /**
     * Wait for a process to get free.
     */
    public void waitForFreeProcess(){
    	
    	while (processCount >= maxProcessCount) {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                //nothing
            }
        }
    }
    
    /**
     * Checks if the current class has a proper 'main' method declared.
     * @return true id 'main' method was found
     */
    public boolean isStandAloneModeSupported(){
    	
    	try {
            Method method = this.getClass().getMethod("main", String[].class);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
    
    /**
     * Start a remote component server on a Javafork object. 
     * @param fork JavaFork used to run the server
     * @return Port number used by the server
     * @throws IOException
     */
    public synchronized int startRemoteServer(JavaFork fork) throws IOException{
    	
    	//get free TCP port to run Cajo server on
        int cajoPort = NetworkUtil.findAvailablePort("127.0.0.1", 5000, 6000);
        if (cajoPort == 0){
        	throw new IOException("No port available to start remote component");
        }
        logger.debug(Thread.currentThread() + " uses " + cajoPort + " as server port");
	
        //add extra environment variables to JVM
        Map<String, String> environment = new HashMap<String, String>();
        //Cajo port
        environment.put("cajo.port", String.valueOf(cajoPort));
        fork.setEnvironment(environment);
        
        //start new JVM with current converter
        fork.start();

        //wait for the remote JVM to start
    	NetworkUtil.waitUntilPortListening("127.0.0.1", cajoPort, 10000);
    	
    	return cajoPort;
    }
    
    /**
     * Get a client proxy of a remote component
     * @param serverPort Server port
     * @param clazz Interface of the proxy
     * @return The proxy object
     * @throws Exception
     */
    public synchronized Object getRemoteComponent(int serverPort, Class clazz) throws Exception{
    	
    	//find Cajo client port available
        int cajoClientPort = NetworkUtil.findAvailablePort("127.0.0.1", 7000, 8000);
        if (cajoClientPort == 0){
        	throw new IOException("No port available to connect to remote component");
        }
        logger.debug(Thread.currentThread() + " uses " + cajoClientPort + " as client port");
        
    	//register cajo
        Cajo cajo = new Cajo(cajoClientPort, null, null);
        cajo.register("127.0.0.1", serverPort);
    	
        //get remote converter
        Object refs[] = cajo.lookup(clazz);
        if (refs.length == 0) {
            //not component found
            return null;
        } else{
        	 return cajo.proxy(refs[0], clazz);
        }
    }
    
    /**
     * Create and return a new JavaFork for remote processing.
     * @return A JavaFork
     */
    public JavaFork buildJavaFork(){
    	
    	JavaFork fork = new JavaFork();
        fork.setRedirectStreams(true);
        fork.setWaitBeforeExiting(false);
        fork.setStartClass(this.getClass());
        
        return fork;
    }
    
    public int getMaxProcessCount() {
        return maxProcessCount;
    }

    public void setMaxProcessCount(int maxProcessCount) {
        this.maxProcessCount = maxProcessCount;
    }

    public int getProcessCount() {
        return processCount;
    }
}
