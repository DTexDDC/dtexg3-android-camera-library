package com.dtex.camera

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.dtex.camera.databinding.FragmentReviewBinding

class ReviewFragment : Fragment(), View.OnClickListener {

    private var _binding: FragmentReviewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CameraViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.retakeButton.setOnClickListener(this)
        binding.doneButton.setOnClickListener(this)
        binding.photoImageView.setImageURI(viewModel.photoUri)
    }

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.retake_button -> {
                parentFragmentManager.popBackStack()
            }

            R.id.done_button -> {
                Intent().also { intent ->
                    intent.putExtra(DtexCamera.ARG_PHOTO_URI, viewModel.photoUri)
                    activity?.setResult(Activity.RESULT_OK, intent)
                    activity?.finish()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        val TAG: String = ReviewFragment::class.java.simpleName
    }
}