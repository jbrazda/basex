(:~
 : Copy database.
 :
 : @author Christian Grün, BaseX Team, BSD License
 :)
module namespace dba = 'dba/databases';

import module namespace html = 'dba/html' at '../lib/html.xqm';
import module namespace utils = 'dba/utils' at '../lib/utils.xqm';

(:~ Top category :)
declare variable $dba:CAT := 'databases';
(:~ Sub category :)
declare variable $dba:SUB := 'database';

(:~
 : Copy database.
 : @param  $name     name of database
 : @param  $newname  new name
 : @param  $do       perform update
 : @return form or redirection
 :)
declare
  %updating
  %rest:POST
  %rest:path('/dba/db-copy')
  %rest:form-param('name',    '{$name}')
  %rest:form-param('newname', '{$newname}')
  %rest:form-param('do',      '{$do}')
  %output:method('html')
function dba:db-copy(
  $name     as xs:string,
  $newname  as xs:string?,
  $do       as xs:string?
) {
  html:update($do, { 'header': ($dba:CAT, $name) }, fn() {
    <tr>
      <td>
        <form method='post' autocomplete='off'>
          <input type='hidden' name='do' value='do'/>
          <input type='hidden' name='name' value='{ $name }'/>
          <h2>{
            html:link('Databases', $dba:CAT), ' » ',
            html:link($name, $dba:SUB, { 'name': $name }), ' » ',
            html:button('db-copy', 'Copy')
          }</h2>
          <table>
            <tr>
              <td>New name:</td>
              <td>
                <input type='text' name='newname' value='{ $newname otherwise $name }' autofocus=''/>
                <div class='small'/>
              </td>
            </tr>
          </table>
        </form>
      </td>
    </tr>
  }, fn() {
    if ($name != $newname) {
      if (db:exists($newname)) {
        error((), 'Database already exists.')
      } else {
        db:copy($name, $newname)
      },
      utils:redirect($dba:SUB, { 'name': $newname, 'info': 'Database was copied.' })
    }
  })
};
