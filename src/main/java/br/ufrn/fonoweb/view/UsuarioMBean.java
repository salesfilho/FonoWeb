/*
 * Copyright 2016 JoinFaces.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package br.ufrn.fonoweb.view;

import br.ufrn.fonoweb.model.Arquivo;
import javax.inject.Named;
import br.ufrn.fonoweb.model.Usuario;
import br.ufrn.fonoweb.service.ArquivoService;
import br.ufrn.fonoweb.service.VozClassifier;
import br.ufrn.fonoweb.service.VozProcessor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.faces.application.FacesMessage;
import javax.inject.Inject;
import javax.sound.sampled.UnsupportedAudioFileException;
import lombok.Getter;
import lombok.Setter;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.UploadedFile;

/**
 *
 * @author yuri
 */
@Named
public class UsuarioMBean extends CrudMBean<Usuario, Long> {

    @Getter
    @Setter
    private boolean uploadFile;

    @Getter
    @Setter
    private List<UploadedFile> listUploadedFiles = new ArrayList<>();

    @Getter
    @Setter
    private Map<String, byte[]> mapUploadedFiles = new HashMap<>();

    @Getter
    @Setter
    private String encodedFileName;

    @Inject
    private ArquivoService arquivoService;
    
    @Inject
    private VozProcessor vozProcessor;

    @Inject
    private VozClassifier vozClassifier;

    public void startUploadFile(Long id) {
        setCurrentState(SEARCH_STATE);
        this.setUploadFile(true);
        setBean(findOne(id));
        listUploadedFiles.clear();
        mapUploadedFiles.clear();
    }

    @Override
    public void changeToInsertState() {
        this.setBean(null);
        super.changeToInsertState();
    }

    @Override
    public void setCurrentState(String currentState) {
        super.setCurrentState(currentState);
        this.setUploadFile(false);
    }

    public void handleFileUpload(FileUploadEvent event) {
        if (event.getFile() != null) {
            mapUploadedFiles.put(event.getFile().getFileName(), event.getFile().getContents());
            saveFiles();
            //addMessage(FacesMessage.SEVERITY_INFO, "Upload:", " Arquivo(s) carregado com sucesso");
        } else {
            addMessage(FacesMessage.SEVERITY_ERROR, "Upload:", " Não foi possível carregado o(s) arquivo(s)");
        }
    }

    public void saveFiles() {
        String fileName = "";
        boolean result = true;
        try {
            for (Map.Entry<String, byte[]> entrySet : mapUploadedFiles.entrySet()) {
                fileName = entrySet.getKey();
                byte[] content = entrySet.getValue();
                String codedFileName = arquivoService.getEncodedFileName(fileName, content);
                arquivoService.saveFile(codedFileName, content);
                //Adiciona os arquivos ao usuário
                this.addArquivoUsuario(fileName, codedFileName);
            }
        } catch (Exception e) {
            result = false;
            addMessage(FacesMessage.SEVERITY_ERROR, "Upload:", " Não foi possível carregado o(s) arquivo(s)");
        }
        if (result == true) {
            processUpdate();
            addMessage(FacesMessage.SEVERITY_INFO, "Upload:", " Arquivo(s) carregado com sucesso");
        }
    }

    private void addArquivoUsuario(String descricao, String fileName) {
        Arquivo arquivo = new Arquivo();
        arquivo.setDataInclusao(new Date());
        arquivo.setDescricao(descricao);
        //arquivo.setDescritor(new DescritorVoz());
        arquivo.setNome(fileName);
        arquivo.setUsuario(getBean());
        getBean().addArquivo(arquivo);
        arquivo.setResultadoAnalise(gerarDiagnostico(arquivo));

    }

    public void processDeleteArquivo(Arquivo arquivo) {
        if (getBean().getArquivos().contains(arquivo)) {
            getBean().getArquivos().remove(arquivo);
            processUpdate();
            setCurrentState(DATAIL_STATE);
        }
    }
    public String gerarDiagnostico(Arquivo arquivo) {
        String diagnostico = "Não analisado.";
        try {
            double[] descritorPaciente = null;
            descritorPaciente = vozProcessor.gerar_classificador(0.5, 1300, 0.77, arquivoService.getDataStore().concat(arquivo.getNome()));
            diagnostico = vozClassifier.resultadoDiagnostico(descritorPaciente);
        } catch (Exception e) {
            diagnostico = "Falha na análise!";
        }
        return diagnostico;

    }

}
