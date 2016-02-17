var checkboxes   = $("input[type='checkbox']"),
	radioButtons = $("input[type='radio']"),
    submitButton = $("input[id='SubmitAnswer']");

checkboxes.click(function() {
    submitButton.attr("disabled", !checkboxes.is(":checked"));
});

radioButtons.click(function() {
    submitButton.attr("disabled", !radioButtons.is(":checked"));
});