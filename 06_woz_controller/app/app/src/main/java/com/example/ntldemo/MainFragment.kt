package com.example.ntldemo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment

class MainFragment : Fragment() {

    private lateinit var etMyId: EditText
    private lateinit var btnRegister: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvConnectionStatus: TextView
    private lateinit var tvAssignedRole: TextView
    private lateinit var tvCurrentCommand: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)

        // 從 MainActivity 獲取狀態並更新 UI
        val mainActivity = activity as? MainActivity
        mainActivity?.let { activity ->
            updateUIFromActivity(activity)
        }
    }

    private fun initViews(view: View) {
        etMyId = view.findViewById(R.id.etMyId)
        btnRegister = view.findViewById(R.id.btnRegister)
        tvStatus = view.findViewById(R.id.tvStatus)
        tvConnectionStatus = view.findViewById(R.id.tvConnectionStatus)
        tvAssignedRole = view.findViewById(R.id.tvAssignedRole)
        tvCurrentCommand = view.findViewById(R.id.tvCurrentCommand)
    }

    private fun updateUIFromActivity(activity: MainActivity) {
        etMyId.setText(activity.myId.ifEmpty { "Robot_${(1000..9999).random()}" })
        updateConnectionStatus(activity.isWebSocketConnected)
        updateRegistrationStatus(activity.isRegistered)
        updateRoleStatus(activity.assignedRole)
    }

    fun updateConnectionStatus(isConnected: Boolean) {
        tvConnectionStatus.text = if (isConnected) "已連接" else "未連接"
    }

    fun updateRegistrationStatus(isRegistered: Boolean) {
        btnRegister.text = if (isRegistered) "取消註冊" else "註冊機器人"
        etMyId.isEnabled = !isRegistered
    }

    fun updateRoleStatus(role: String) {
        tvAssignedRole.text = "角色: ${role.ifEmpty { "未分配" }}"
    }

    fun updateStatus(status: String) {
        tvStatus.text = status
    }

    fun updateCurrentCommand(command: String) {
        tvCurrentCommand.text = command
    }
}