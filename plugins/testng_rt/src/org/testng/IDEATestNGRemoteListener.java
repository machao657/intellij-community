package org.testng;

import com.intellij.rt.execution.junit.ComparisonFailureData;
import jetbrains.buildServer.messages.serviceMessages.MapSerializerUtil;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes;
import org.testng.internal.IResultListener;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: anna
 * Date: 5/22/13
 */
public class IDEATestNGRemoteListener implements ISuiteListener, IResultListener{

  public static final String INVOCATION_NUMBER = "invocation number: ";
  private String myCurrentClassName;
  private String myMethodName;
  private int    myInvocationCount = 0;

  private static String escapeName(String str) {
    return MapSerializerUtil.escapeStr(str, MapSerializerUtil.STD_ESCAPER);
  }

  public void onConfigurationSuccess(ITestResult result) {
    final String className = getShortName(result.getTestClass().getName());
    System.out.println("##teamcity[testSuiteStarted name=\'" + escapeName(className) + "\']");
    final String methodName = result.getMethod().getMethodName();
    System.out.println("##teamcity[testStarted name=\'" + escapeName(methodName) + "\']");
    onTestSuccess(result);
    System.out.println("\n##teamcity[testSuiteFinished name=\'" + escapeName(className) + "\']");
  }

  public void onConfigurationFailure(ITestResult result) {
    final String className = getShortName(result.getTestClass().getName());
    System.out.println("##teamcity[testSuiteStarted name=\'" + escapeName(className) + "\']");
    final String methodName = result.getMethod().getMethodName();
    System.out.println("##teamcity[testStarted name=\'" + escapeName(methodName) + "\']");
    onTestFailure(result);
    System.out.println("\n##teamcity[testSuiteFinished name=\'" + escapeName(className) + "\']");
  }

  public void onConfigurationSkip(ITestResult itr) {
  }

  public void onStart(ISuite suite) {
    System.out.println("##teamcity[enteredTheMatrix]");
    System.out.println("##teamcity[testSuiteStarted name =\'" + escapeName(suite.getName()) + "\']");
  }

  public void onFinish(ISuite suite) {
    System.out.println("##teamcity[testSuiteFinished name=\'" + escapeName(suite.getName()) + "\']");
  }

  public void onTestStart(ITestResult result) {
    final String className = getShortName(result.getTestClass().getName());
    if (myCurrentClassName == null || !myCurrentClassName.equals(className)) {
      if (myCurrentClassName != null) {
        System.out.println("##teamcity[testSuiteFinished name=\'" + escapeName(myCurrentClassName) + "\']");
      }
      System.out.println("##teamcity[testSuiteStarted name =\'" + escapeName(className) + "\']");
      myCurrentClassName = className;
      myInvocationCount = 0;
    }
    String methodName = getMethodName(result, false);
    System.out.println("##teamcity[testStarted name=\'" + escapeName(methodName) +
                       "\' locationHint=\'java:test://" + escapeName(className + "." + methodName) + "\']");
  }

  private String getMethodName(ITestResult result) {
    return getMethodName(result, true);
  }

  private String getMethodName(ITestResult result, boolean changeCount) {
    String methodName = result.getMethod().getMethodName();
    final Object[] parameters = result.getParameters();
    if (!methodName.equals(myMethodName)) {
      myInvocationCount = 0;
      myMethodName = methodName;
    }
    if (parameters.length > 0) {
      final List<Integer> invocationNumbers = result.getMethod().getInvocationNumbers();
      methodName += "[" + parameters[0].toString() + " (" + INVOCATION_NUMBER + 
                    (invocationNumbers.isEmpty() ? myInvocationCount : invocationNumbers.get(myInvocationCount)) + ")" + "]";
      if (changeCount) {
        myInvocationCount++;
      }
    }
    return methodName;
  }

  public void onTestSuccess(ITestResult result) {
    System.out.println("\n##teamcity[testFinished name=\'" + escapeName(getMethodName(result)) + "\']");
  }

  public String getTrace(Throwable tr) {
    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    tr.printStackTrace(writer);
    StringBuffer buffer = stringWriter.getBuffer();
    return buffer.toString();
  }

  public void onTestFailure(ITestResult result) {
    final Throwable ex = result.getThrowable();
    final String trace = getTrace(ex);
    final Map<String, String> attrs = new HashMap<String, String>();
    final String methodName = getMethodName(result);
    attrs.put("name", methodName);
    final String failureMessage = ex.getMessage();
    ComparisonFailureData notification;
    try {
      notification = TestNGExpectedPatterns.createExceptionNotification(failureMessage);
    }
    catch (Throwable e) {
      notification = null;
    }
    ComparisonFailureData.registerSMAttributes(notification, trace, failureMessage, attrs);
    System.out.println(ServiceMessage.asString(ServiceMessageTypes.TEST_FAILED, attrs));
    System.out.println("\n##teamcity[testFinished name=\'" + escapeName(methodName) + "\']");
  }

  public void onTestSkipped(ITestResult result) {
    System.out.println("\n##teamcity[testFinished name=\'" + escapeName(getMethodName(result)) + "\']");
  }

  public void onTestFailedButWithinSuccessPercentage(ITestResult result) {}

  public void onStart(ITestContext context) {}

  public void onFinish(ITestContext context) {
    if (myCurrentClassName != null) {
      System.out.println("##teamcity[testSuiteFinished name=\'" + escapeName(myCurrentClassName) + "\']");
    }
  }

  protected static String getShortName(String fqName) {
    int lastPointIdx = fqName.lastIndexOf('.');
    if (lastPointIdx >= 0) {
      return fqName.substring(lastPointIdx + 1);
    }
    return fqName;
  }
}
