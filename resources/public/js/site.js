
function get_scroll_height() {
  return window.pageYOffset
}

function get_scroll_width() {
  return window.pageXOffset
}

function noclick(e) {
  e.preventDefault();
  return false;
}

function fix_text_areas() {
  var textareas = document.getElementsByTagName('textarea');

  for (var i= 0; i < textareas.length; ++i) {
    textarea = textareas[i];
    textarea.addEventListener('keydown', autosize);
    textarea.addEventListener('focus', autosize);

    function autosize(){
      var el = this;
      setTimeout(function(){
        el.style.cssText = 'height:auto; padding:0';
        // for box-sizing other than "content-box" use:
        // el.style.cssText = '-moz-box-sizing:content-box';
        el.style.cssText = 'height:' + el.scrollHeight + 'px';
      },0);
    }
  }

  setTimeout(fix_text_areas, 1000);
}

function everything() {
  fix_text_areas()
}

function highlight_text_area(id) {
  var note = document.getElementById(id);
  setTimeout(function () {note.children[0].focus()}, 100);
}

function display_state(id) {
  var note = document.getElementById(id);
  note.children[1].innerHTML = content;
}

window.onload = everything;

