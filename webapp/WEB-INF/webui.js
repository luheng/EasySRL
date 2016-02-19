$(function() {
    var checkboxes   = $("input[type='checkbox']"),
        radioButtons = $("input[type='radio']"),
        submitButton = $("button[id='SubmitAnswer']");

    checkboxes.click(function() {
        submitButton.attr("disabled", !checkboxes.is(":checked"));
    });

    radioButtons.click(function() {
        console.log(submitButton);
        console.log(!radioButtons.is(":checked"));
        submitButton.attr("disabled", !radioButtons.is(":checked"));
    });
});