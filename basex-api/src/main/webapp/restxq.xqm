(:~
 : This module contains some basic examples for RESTXQ annotations.
 : @author BaseX Team
 :)
module namespace page = 'http://basex.org/examples/web-page';

(:~
 : Generates a welcome page.
 : @return HTML page
 :)
declare
  %rest:GET
  %rest:path('')
  %output:method('html')
function page:start(
) as element(Q{http://www.w3.org/1999/xhtml}html) {
  <html xmlns='http://www.w3.org/1999/xhtml'>
    <head>
      <title>BaseX HTTP Services</title>
      <link rel='stylesheet' type='text/css' href='static/style.css'/>
    </head>
    <body>
      <div class='right'><a href='/'><img src='static/basex.svg'/></a></div>
      <h1>BaseX HTTP Services</h1>
      <div>Welcome to the BaseX HTTP Services. They allow you to:</div>
      <ul>
        <li>create web applications and services with
          <a href='https://docs.basex.org/main/RESTXQ'>RESTXQ</a>,</li>
        <li>use full-duplex communication with
          <a href='https://docs.basex.org/main/WebSockets'>WebSockets</a>, and </li>
        <li>query and modify databases via <a href='https://docs.basex.org/main/REST'>REST</a>
          (try <a href='rest'>here</a>).</li>
      </ul>

      <p>Find more information on the
        <a href='https://docs.basex.org/main/Web_Application'>Web Application</a>
        page in our documentation.</p>

      <p>The following sample applications give you a glimpse of how applications
        can be written with BaseX:</p>

      <h3><a href='dba'>DBA: Database Administration</a></h3>

      <p>The Database Administration interface is completely written in RESTXQ.<br/>
        You can view the source code to learn how to build complex web applications with XQuery.
      </p>

      <h3><a href='chat'>WebSocket Chat</a></h3>

      <p>The chat application demonstrates how bidirectional communication
        is realized with BaseX.<br/>
        For a better experience when testing the chat, consider the following steps:
      </p>
        
      <ol>
        <li> Create different database users first (e.g. via the DBA).</li>
        <li> Open two different browsers and log in with different users.</li>
      </ol>
    </body>
  </html>
};
