$(document).ready(function (){
    var headerRow;
    $('select.blameSelect').change(function(e){
        assign(makeUrl(e.target));
    });
    if(/history/i.test(document.URL) != true || $('td.testStatus').first().parent().index() != -1) {
        if($('td.testStatus').first().parent().index() == 1) {
            headerRow = $('td.testStatus').first().parent().prev();
        } else if ($('td.testStatus').first().parent().index() == 0) {
            headerRow = $('td.testStatus').first().parent().parent().prev().children().first();
        }
        var assignedToIndex = $('td.testFailureAssignment').first().index();  // Index for assigned to col
        var statusIndex = $('td.testStatus').first().index();                 // Index for status col

        var assignedToHeader = $("<td>").addClass("pane-header").text("Assigned To");
        var statusHeader = $("<td>").addClass("pane-header").text("Status");

        if(assignedToIndex > 0) {
            headerRow.children().eq(assignedToIndex-1).after(assignedToHeader);
        }
        if(assignedToIndex == -1){}
        if(assignedToIndex == 0 || assignedToIndex < -1) {
            headerRow.children().eq(assignedToIndex).before(assignedToHeader);
        }
        if(statusIndex > 0) {
            headerRow.children().eq(statusIndex-1).after(statusHeader);
        }
        if(statusIndex == -1){}
        if(statusIndex == 0 || statusIndex < -1) {
            headerRow.children().eq(statusIndex).before(statusHeader);
        }

        var tableRows = $("td.testStatus").first().parent().parent().children();
        var tableRow;
        var status;

        var firstContentRow = $('td.pane').first().parent().index();  // First row in the table with content.  Not Headers.

        for (var index = firstContentRow; index < tableRows.size(); index++) {
            tableRow = tableRows.eq(index);
            status = tableRow.find('td.testStatus').text();

            if (status == 'Accepted') {
                tableRow.css('color', '#00AA00')
            } else if (status == 'Resolved' || status == 'Done') {
                tableRow.css('color', '#999999')
            } else if (status != 'Untracked') {
                tableRow.css('color','#0000FF');
            }
        }
    }
});

function makeUrl(target){
    return document.URL.substring(0,document.URL.indexOf('testReport')+11)+$(target).parent().find('input:hidden#blameUrl').attr('value');
}

function assign(url) {
    $.get(url, { blameId: $(this).val() } );
}