var rootUrl = window.location.protocol+'//'+window.location.hostname+":"+window.location.port;

function loadCss() {
    var cssfile1 = document.createElement('link');
    cssfile1.setAttribute("rel", "stylesheet");
    cssfile1.setAttribute("type", "text/css");
	// TODO: I have hard coded these until we can create a correct rootURL. Predecessor to public release
    //cssfile1.setAttribute("href", "http://ci.attask.com/jenkins/plugin/AtTaskForJenkins/TestResultsStyle.css");
    cssfile1.setAttribute("href", rootUrl + "/plugin/AtTaskForJenkins/TestResultsStyle.css");
    document.getElementsByTagName("head")[0].appendChild(cssfile1);
}

function loadJavascriptFiles(){
    var tableTransformer = document.createElement('script');
    tableTransformer.setAttribute("type", "text/javascript");
    // TODO: I have hard coded these until we can create a correct rootURL. Predecessor to public release
    tableTransformer.setAttribute("src", rootUrl+"/plugin/AtTaskForJenkins/TableTransformer.js");
    //tableTransformer.setAttribute("src", "http://ci.attask.com/jenkins/plugin/AtTaskForJenkins/TableTransformer.js");

    document.getElementsByTagName("head")[0].appendChild(tableTransformer);

    // Popup is not functional currently
//    var popupRenderer = document.createElement('script');
//    popupRenderer.setAttribute("type", "text/javascript");
//    // TODO: I have hard coded these until we can create a correct rootURL. Predecessor to public release
//    popupRenderer.setAttribute("src", rootUrl+"/plugin/AtTaskForJenkins/CommittersPopupRenderer.js");
//    //popupRenderer.setAttribute("src", "http://ci.attask.com/jenkins/plugin/AtTaskForJenkins/CommittersPopupRenderer.js");
//
//    document.getElementsByTagName("head")[0].appendChild(popupRenderer);
}

function shouldLoadDependencies(){
    if(/testReport/i.test(document.URL)) {
        loadCss();
        loadJquery();
    }
}

function loadJquery(){
    getScript('http://ajax.googleapis.com/ajax/libs/jquery/1.7.1/jquery.min.js', function() {

        if (typeof jQuery=='undefined') {

            // Super failsafe - still somehow failed...

        } else { // jQuery loaded! Make sure to use .noConflict just in case

            if (thisPageUsingOtherJSLibrary) {

                loadJavascriptFiles();

            } else {// Use .noConflict(), then run your jQuery Code

                loadJavascriptFiles();
            }
        }
    });
}

// Only do anything if jQuery isn't defined
if (typeof jQuery == 'undefined') {

    if (typeof $ == 'function') {
        // warning, global var
        thisPageUsingOtherJSLibrary = true;
    }

    function getScript(url, success) {

        var script     = document.createElement('script');
        script.src = url;

        var head = document.getElementsByTagName('head')[0],
            done = false;

        // Attach handlers for all browsers
        script.onload = script.onreadystatechange = function() {

            if (!done && (!this.readyState || this.readyState == 'loaded' || this.readyState == 'complete')) {

                done = true;

                success();

                script.onload = script.onreadystatechange = null;
                head.removeChild(script);
            };
        };
        head.appendChild(script);
    };

} else { // jQuery was already loaded
    loadJavascriptFiles();
};

shouldLoadDependencies();