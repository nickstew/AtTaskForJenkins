/**
 * User: nicholasstewart
 * Date: 3/14/12
 * Time: 2:30 PM
 */

$(document).ready(function(){
    var rows = $('select.blameSelect').parent();
    rows.each(function(index){
        $(this).hover(openPopup, closePopup);
    });
});


var openPopup = (function(e) {
    var div;
    if(window.commitTooltip) {
        div = window.commitTooltip;
    } else {
        div = document.createElement("div");
        div.setAttribute('id', 'commitTooltip');
        window.commitTooltip = div;
        document.body.appendChild(div);
    }

    var url = document.URL.substring(0,document.URL.lastIndexOf('testReport')) + "api/json";
    showContent(div, url, e.clientX + window.scrollX, e.clientY + window.scrollY);
});

var closePopup =(function(e) {
    var parent = e.target;
    while(parent && parent != window.commitTooltip) {
        parent = parent.parentElement;
    }

    if(parent != window.commitTooltip) {
        window.commitTooltip.style.display = "none";
    }
});

var showContent = (function(element, url, x, y) {
    new Ajax.Request(url, {
        method:'get',
        onSuccess:function (transport) {
            var json = transport.response;
            var result = eval('('+json+')');

            var culprits = result.culprits;
            if(culprits.length <= 0) {
                return;
            }

            var html = '<div class="header">Committers</div>';
            html += '<hr/>';
            html += '<div class="committers">';
            culprits.each(function(it) {
                html += 	'<div>Name: <span class="name"><a href="'+it.absoluteUrl+'">'+it.fullName+'</a></span></div>';
            });
            html += '</div>';

            element.innerHTML = html;
            element.style.left = x + "px";
            element.style.top = y + "px";
            element.style.display = "block";
        }
    });
});