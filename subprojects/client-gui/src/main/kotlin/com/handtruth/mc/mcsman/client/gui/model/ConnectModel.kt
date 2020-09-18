package com.handtruth.mc.mcsman.client.gui.model

import javafx.beans.property.SimpleStringProperty
import tornadofx.ItemViewModel
import tornadofx.getValue
import tornadofx.setValue

class ConnectModel {
    val serverProperty = SimpleStringProperty()
    var server: String by serverProperty
}

class ConnectViewModel : ItemViewModel<ConnectModel>(ConnectModel()) {
    val server = bind(ConnectModel::serverProperty)
}
