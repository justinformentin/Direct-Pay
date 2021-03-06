$(function(){
    function showHide() {
        if ($('#manualauto_mode').attr("checked") == 'checked') {
            $('#manual_operations').hide();
            $('#automatic_operations').show();
            $('.class_manual').hide();
            $('.class_automatic').show();
        } else {
            $('#manual_operations').show();
            $('#automatic_operations').hide();
            $('.class_manual').show();
            $('.class_automatic').hide();
        }
    }

    var operations_panels_visible = false;
    $('#btn_operation_panels').click(function () {
        if (operations_panels_visible) {
            $('#operation_panels').hide();
            $('#btn_operation_panels_caption').html(Messages('directpay.overview.showoperationstutorial'));
            operations_panels_visible = false;
        } else {
            $('#operation_panels').show();
            operations_panels_visible = true;
            $('#btn_operation_panels_caption').html(Messages('directpay.overview.hideoperationstutorial'));
            resizeDiv();
        }
    });


    function showFeeMessages() {
        $('#calc_convertion_rate').html(NumberFormat((100 - parseFloat($('#hidden_fees_information').attr('fee_tofiat_percent'))) * 0.01, 3));
        $('#calc_withdraw_preferential_bank_fee').html(NumberFormat($('#hidden_fees_information').attr('nominal_fee_withdrawal_preferential_bank'), 2));
        $('#calc_withdraw_not_preferential_bank_extra_fee').html(NumberFormat(parseFloat($('#hidden_fees_information').attr('nominal_fee_withdrawal_not_preferential_bank') - parseFloat($('#hidden_fees_information').attr('nominal_fee_withdrawal_preferential_bank'))), 2));
        $('#calc_convertion_rate2').html(NumberFormat((100 - parseFloat($('#hidden_fees_information').attr('fee_tofiat_percent'))) * 0.01, 3));
        $('#calc_withdraw_preferential_bank_fee2').html($('#calc_withdraw_preferential_bank_fee').text());
        $('#calc_withdraw_not_preferential_bank_extra_fee2').html($('#calc_withdraw_not_preferential_bank_extra_fee').text());
    }


    $(document).ready(function () {
        $('#manualauto_mode').change(function () {
            showHide();
            var manualauto_mode = ($('#manualauto_mode').attr("checked") != 'checked');
            API.change_manualauto(manualauto_mode).success(function () {
                $.pnotify({
                    title: Messages("messages.api.success"),
                    text: Messages("messages.api.success.manualautomodechanged"),
                    styling: 'bootstrap',
                    type: 'info',
                    text_escape: true
                });
            });
            resizeDiv()
        });


        $(window).resize(function() {
            resizeDiv()
        });
    });
    showHide();
    resizeDiv();

    showFeeMessages();
    FillDocumentsNotVerifiedMessages ();
});

function resizeDiv (){
    $('.variable_height').css('height', parseInt(130 + 100000/$('.variable_height').width())); // choose numerical parameters if text is shorter or longer
    $('.variable_height2').css('height', parseInt(190 + 100000/$('.variable_height2').width())); // choose numerical parameters if text is shorter or longer
}

function FillDocumentsNotVerifiedMessages () {
    var list_of_documents = $('#hidden_listofdocuments').attr('listofdocuments_message');
    list_of_documents += ' ' + $('#hidden_listofdocuments').attr('first_name');
    list_of_documents += ', ' + $('#hidden_listofdocuments').attr('last_name');
    if($('#hidden_listofdocuments').attr('country_doc1')) list_of_documents += ', ' + $('#hidden_listofdocuments').attr('country_doc1');
    if($('#hidden_listofdocuments').attr('country_doc2')) list_of_documents += ', ' + $('#hidden_listofdocuments').attr('country_doc2');
    if($('#hidden_listofdocuments').attr('country_doc3')) list_of_documents += ', ' + $('#hidden_listofdocuments').attr('country_doc3');
    if($('#hidden_listofdocuments').attr('country_doc4')) list_of_documents += ', ' + $('#hidden_listofdocuments').attr('country_doc4');
    if($('#hidden_listofdocuments').attr('country_doc5')) list_of_documents += ', ' + $('#hidden_listofdocuments').attr('country_doc5');
    $('#incomplete_docs').attr('title', list_of_documents);
}
