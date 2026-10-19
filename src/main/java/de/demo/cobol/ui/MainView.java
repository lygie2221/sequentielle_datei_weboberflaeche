package de.demo.cobol.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import de.demo.cobol.UploadSlot;
import de.demo.cobol.model.*;
import de.demo.cobol.service.*;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import java.io.ByteArrayInputStream;

@Route("")
@PageTitle("COBOL Record Explorer")
public class MainView extends VerticalLayout {
    private final UploadSlot copybook = new UploadSlot(256 * 1024);
    private final UploadSlot data = new UploadSlot(10 * 1024 * 1024);
    private final ExplorerSession session = new ExplorerSession();
    private final ComboBox<RecordCodec.Encoding> encoding = new ComboBox<>("Kodierung der Datendatei");
    private final ComboBox<RecordCodec.Format> format = new ComboBox<>("Recordformat");
    private final Paragraph status = new Paragraph("Bitte Copybook und Datendatei hochladen.");
    private final Grid<FieldLayout> layoutGrid = new Grid<>();
    private final Grid<DataRecord> grid = new Grid<>();
    private final Button download = new Button("Änderungen als Datei speichern", e -> downloadFile());
    private final Anchor downloadLink = new Anchor();

    public MainView() {
        setWidthFull();
        setMaxWidth("1600px");
        getStyle().set("margin", "0 auto");
        add(new H1("COBOL Record Explorer"), new Paragraph(
                "Copybook und Datendatei laden, Datensätze bearbeiten und als Datei herunterladen."));
        addUpload("1. COBOL-Copybook", copybook, 256 * 1024);
        addUpload("2. Sequenzielle Datendatei", data, 10 * 1024 * 1024);
        encoding.setItems(RecordCodec.Encoding.values());
        encoding.setValue(RecordCodec.Encoding.LATIN1);
        encoding.setAllowCustomValue(false);
        encoding.setHelperText("Beispieldatei: ISO-8859-1. UTF-8 wird wegen variabler Bytebreite nicht unterstützt.");
        format.setItems(RecordCodec.Format.values());
        format.setValue(RecordCodec.Format.LF);
        format.setWidth("320px");
        format.setHelperText("kunden.dat: LF. Jede Zeile muss genau die Copybook-Länge haben, plus Trenner.");
        encoding.addValueChangeListener(e -> reload());
        format.addValueChangeListener(e -> reload());
        add(encoding, format, new Paragraph("Neue Uploads oder Änderungen an Kodierung/Format laden die Originaldaten neu und verwerfen bisherige Bearbeitungen."));
        status.getElement().setAttribute("role", "status");
        add(status, new H2("Interpretiertes Layout"));
        layoutGrid.addColumn(FieldLayout::path).setHeader("Feldpfad").setAutoWidth(true);
        layoutGrid.addColumn(f -> f.type() == FieldLayout.Type.TEXT ? "Text" : "Numerisch").setHeader("Typ");
        layoutGrid.addColumn(FieldLayout::offset).setHeader("Byteoffset (ab 0)");
        layoutGrid.addColumn(FieldLayout::length).setHeader("Bytes");
        layoutGrid.addColumn(FieldLayout::scale).setHeader("Nachkommastellen");
        layoutGrid.addColumn(f -> f.signed() ? "Overpunch" : "–").setHeader("Vorzeichen");
        layoutGrid.setHeight("220px");
        add(layoutGrid, new H2("Datensätze"), new Paragraph(
                "Mit Bearbeiten eine Zeile öffnen. Übernehmen validiert alle Felder; Abbrechen verwirft die Eingabe. "
                        + "Textfelder benötigen exakt die angegebene Byteanzahl einschließlich Leerzeichen. Zahlen erlauben Komma oder Punkt."));
        grid.setHeight("480px");
        grid.setWidthFull();
        download.setEnabled(false);
        downloadLink.getStyle().set("display", "none");
        downloadLink.setDownload(true);
        add(grid, download, downloadLink, new Paragraph(
                "Vor dem Speichern die geöffnete Zeile übernehmen oder abbrechen. Der Browser lädt die bearbeitete Datei als daten-bearbeitet.dat herunter. "
                        + "Zum Ersetzen des Originals die heruntergeladene Datei entsprechend speichern. Neuladen der Seite verwirft die Daten im Speicher."));
    }

    @SuppressWarnings("deprecation")
    private void addUpload(String title, UploadSlot slot, int limit) {
        Paragraph uploadStatus = new Paragraph("Noch keine Datei geladen.");
        uploadStatus.getElement().setAttribute("role", "status");
        Upload upload = new Upload((name, mime) -> slot.begin());
        upload.setMaxFiles(1);
        upload.setMaxFileSize(limit);
        upload.setUploadButton(new Button("Datei auswählen"));
        upload.addSucceededListener(event -> {
            if (slot.size() == 0) {
                slot.clear();
                uploadStatus.setText("Die Datei ist leer. Bitte eine Datei mit Inhalt auswählen.");
            } else uploadStatus.setText("Geladen: " + event.getFileName() + " (" + slot.size() + " Bytes).");
            upload.clearFileList();
            reload();
        });
        upload.addFileRejectedListener(event -> uploadStatus.setText(
                "Datei abgelehnt. Maximal " + limit + " Bytes. " + event.getErrorMessage()));
        upload.addFailedListener(event -> {
            slot.clear();
            upload.clearFileList();
            uploadStatus.setText("Upload fehlgeschlagen. Bitte erneut versuchen (Limit: " + limit + " Bytes).");
            reload();
        });
        Button clear = new Button("Datei verwerfen", event -> {
            slot.clear();
            upload.clearFileList();
            uploadStatus.setText("Noch keine Datei geladen.");
            reload();
        });
        add(new H2(title), new Paragraph("Maximal " + limit + " Bytes."), upload, clear, uploadStatus);
    }

    private void reload() {
        download.setEnabled(false);
        downloadLink.removeHref();
        grid.getEditor().cancel();
        grid.setItems(java.util.List.of());
        grid.removeAllColumns();
        layoutGrid.setItems(java.util.List.of());
        session.clear();
        if (copybook.size() == 0) { status.setText("Bitte ein Copybook hochladen."); return; }
        try {
            session.loadLayout(copybook.content());
            layoutGrid.setItems(session.layout().fields());
            if (data.size() == 0) {
                status.setText("Copybook gültig: " + session.layout().recordLength() + " Bytes/Record. Bitte Datendatei hochladen.");
                return;
            }
            if (encoding.getValue() == null || format.getValue() == null) {
                status.setText("Bitte Kodierung und Recordformat auswählen."); return;
            }
            session.loadData(data.content(), encoding.getValue(), format.getValue());
            configureGrid();
            download.setEnabled(true);
            long errors = session.rows().stream().filter(r -> !r.errors().isEmpty()).count();
            status.setText(session.rows().size() + " Datensätze geladen, " + session.layout().recordLength()
                    + " Bytes/Record. " + errors + " Datensätze mit Feldfehlern (siehe Fehlerspalte).");
        } catch (IllegalArgumentException e) { status.setText("Dateien konnten nicht ausgewertet werden: " + e.getMessage()); }
    }

    private void downloadFile() {
        if (grid.getEditor().isOpen()) {
            status.setText("Bitte die geöffnete Zeile zuerst übernehmen oder abbrechen, dann die Datei speichern.");
            return;
        }
        try {
            byte[] snapshot = session.exportData();
            downloadLink.setHref(DownloadHandler.fromInputStream(event -> new DownloadResponse(
                    new ByteArrayInputStream(snapshot), "daten-bearbeitet.dat", "application/octet-stream", snapshot.length)));
            downloadLink.getElement().executeJs("this.click()");
            status.setText("Download angefordert: daten-bearbeitet.dat (" + snapshot.length
                    + " Bytes). Kodierung und Recordformat bleiben erhalten. Speicherort im Browser prüfen.");
        } catch (IllegalArgumentException e) { status.setText("Speichern nicht möglich: " + e.getMessage()); }
    }

    private void configureGrid() {
        Binder<DataRecord> binder = new Binder<>();
        var editor = grid.getEditor();
        editor.setBinder(binder);
        editor.setBuffered(true);
        grid.addColumn(DataRecord::number).setHeader("Satz").setWidth("80px").setFlexGrow(0).setFrozen(true);
        for (FieldLayout field : session.layout().fields()) {
            TextField input = new TextField();
            input.setWidthFull();
            input.setAriaLabel(field.path());
            input.setTooltipText(field.length() + " Bytes; " + field.path());
            binder.forField(input).withValidator((value, context) -> {
                try {
                    session.validate(field, value, encoding.getValue());
                    return ValidationResult.ok();
                } catch (IllegalArgumentException e) { return ValidationResult.error(e.getMessage()); }
            }).bind(row -> row.value(field.path()),
                    (row, value) -> session.edit(row, field, value, encoding.getValue()));
            grid.addColumn(row -> row.value(field.path())).setHeader(field.path())
                    .setWidth(Math.max(220, Math.min(field.length() * 12, 400)) + "px")
                    .setFlexGrow(0).setResizable(true).setEditorComponent(input);
        }
        grid.addColumn(row -> row.edited() ? "Geändert" : "Original").setHeader("Stand").setWidth("120px").setFlexGrow(0);
        grid.addColumn(row -> row.errors().entrySet().stream().map(e -> e.getKey() + ": " + e.getValue())
                        .collect(java.util.stream.Collectors.joining("; ")))
                .setHeader("Feldfehler").setWidth("320px").setFlexGrow(0);
        Button save = new Button("Übernehmen", e -> {
            if (editor.save()) status.setText("Änderungen im Speicher übernommen. Die Datei auf der Festplatte bleibt unverändert.");
            else status.setText("Ungültige Eingabe. Bitte die markierten Felder korrigieren.");
        });
        Button cancel = new Button("Abbrechen", e -> editor.cancel());
        grid.addComponentColumn(row -> new Button("Bearbeiten", e -> {
            if (editor.isOpen()) {
                status.setText("Bitte die geöffnete Zeile zuerst übernehmen oder abbrechen.");
                return;
            }
            editor.editItem(row);
        })).setHeader("Aktion").setWidth("260px").setFlexGrow(0).setFrozenToEnd(true)
                .setEditorComponent(new HorizontalLayout(save, cancel));
        grid.setItems(session.rows());
    }
}
